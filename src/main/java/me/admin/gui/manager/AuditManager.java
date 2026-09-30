package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Append-only, hash-chained audit journal with bounded async I/O and HMAC checkpoints. */
public final class AuditManager implements AutoCloseable {

    public record AuditEntry(UUID id, long timestamp, UUID actorUuid, String actorName,
                             String action, UUID targetUuid, String targetName,
                             String details, String previousHash, String hash) {}

    public record Health(int queued, int capacity, boolean writerAlive, boolean writing, long written,
                         long failedWrites, long recoveredWrites, long checkpoints, long rejectedEntries,
                         boolean failClosed, String lastError) {}

    record ChainVerification(boolean valid, int line, String reason, String lastHash, long entries) {}

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final DateTimeFormatter SEGMENT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

    private final AdvancedModeratorGUI plugin;
    private final Path logFile;
    private final Path checkpointFile;
    private final Path recoveryFile;
    private final byte[] hmacKey;
    private final Deque<AuditEntry> recent = new ArrayDeque<>();
    private final int memoryLimit;
    private final int checkpointInterval;
    private final AuditIngress<AuditEntry> ingress;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean writerRunning = new AtomicBoolean(true);
    private final AtomicBoolean writing = new AtomicBoolean();
    private final AtomicBoolean failClosed = new AtomicBoolean();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong recoveredWrites = new AtomicLong();
    private final AtomicLong checkpoints = new AtomicLong();
    private final Thread writerThread;
    private volatile String lastHash = "GENESIS";
    private volatile boolean integrityValid = true;
    private volatile boolean activeLogWritable = true;
    private volatile AuditEntry lastPersistedEntry;
    private volatile String lastError = "";
    private volatile long sequence;

    public AuditManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.logFile = root.resolve("audit.log");
        this.checkpointFile = root.resolve("audit.checkpoints");
        this.recoveryFile = root.resolve("audit-recovery.log");
        this.memoryLimit = Math.max(100, plugin.getConfig().getInt("audit.memory-limit", 2000));
        this.checkpointInterval = Math.clamp(plugin.getConfig().getInt("audit.checkpoint-interval", 100), 10, 10_000);
        this.ingress = new AuditIngress<>(Math.clamp(plugin.getConfig().getInt("audit.queue-capacity", 2048), 128, 65_536));
        this.hmacKey = loadOrCreateKey(root.resolve("audit.key"), root);
        loadAndVerify();
        this.writerThread = new Thread(this::writerLoop, "amgui-audit-writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    public void record(CommandSender actor, String action, String targetName, UUID targetUuid, String details) {
        UUID actorUuid = actor instanceof Player player ? player.getUniqueId() : null;
        tryRecord(actorUuid, actor == null ? "System" : actor.getName(), action, targetName, targetUuid, details);
    }

    public void record(UUID actorUuid, String actorName, String action,
                       String targetName, UUID targetUuid, String details) {
        tryRecord(actorUuid, actorName, action, targetName, targetUuid, details);
    }

    /**
     * Never waits and never performs file I/O on the caller. False means the
     * action was not journaled and security-sensitive callers must fail closed.
     */
    public boolean tryRecord(UUID actorUuid, String actorName, String action,
                             String targetName, UUID targetUuid, String details) {
        AuditEntry entry;
        synchronized (this) {
            if (!accepting.get()) {
                failedWrites.incrementAndGet();
                lastError = "audit writer is closed";
                failClosed.set(true);
                return false;
            }
            entry = createEntry(actorUuid, actorName, action, targetUuid, targetName, details);
            if (!ingress.offer(entry)) {
                failedWrites.incrementAndGet();
                lastError = "audit queue capacity exceeded; critical actions are fail-closed";
                failClosed.set(true);
                return false;
            }
            enqueued.incrementAndGet();
            lastHash = entry.hash();
            addRecent(entry);
        }
        ModerationSessionManager sessions = plugin.getModerationSessionManager();
        if (sessions != null) sessions.recordAudit(actorUuid, targetUuid, action, details);
        return true;
    }

    private AuditEntry createEntry(UUID actorUuid, String actorName, String action,
                                   UUID targetUuid, String targetName, String details) {
        UUID id = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        AuditEntry unsigned = new AuditEntry(id, timestamp, actorUuid, bounded(actorName, 128), bounded(action, 128),
                targetUuid, bounded(targetName, 128), bounded(details, 8192), lastHash, "");
        return new AuditEntry(id, timestamp, actorUuid, unsigned.actorName(), unsigned.action(), targetUuid,
                unsigned.targetName(), unsigned.details(), lastHash, sha256(serializePayload(unsigned)));
    }

    private void writerLoop() {
        while (writerRunning.get() || !ingress.isEmpty()) {
            AuditEntry entry = null;
            try {
                entry = ingress.poll(250, TimeUnit.MILLISECONDS);
                if (entry == null) continue;
                writing.set(true);
                appendWithRetry(entry);
            } catch (InterruptedException interrupted) {
                if (writerRunning.get()) Thread.currentThread().interrupt();
            } finally {
                if (entry != null) processed.incrementAndGet();
                writing.set(false);
            }
        }
    }

    private void appendWithRetry(AuditEntry entry) {
        if (!activeLogWritable) {
            emergencyAppend(entry, "active audit segment disabled after an I/O failure");
            return;
        }
        Exception failure = null;
        boolean persisted = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            long originalSize = 0L;
            try {
                Files.createDirectories(logFile.getParent());
                originalSize = Files.exists(logFile) ? Files.size(logFile) : 0L;
                YamlPersistenceService.trackedFileMutation(logFile.getParent(), logFile, () ->
                        Files.writeString(logFile, serializeLine(entry) + System.lineSeparator(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                persisted = true;
                break;
            } catch (Exception error) {
                failure = error;
                // A failed append may have written a prefix. Restore the known single-writer boundary before retrying.
                long truncateSize = originalSize;
                try {
                    YamlPersistenceService.trackedFileMutation(logFile.getParent(), logFile, () -> {
                        try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.WRITE)) {
                            channel.truncate(truncateSize);
                        }
                    });
                }
                catch (Exception truncateError) { failure.addSuppressed(truncateError); break; }
                try { Thread.sleep(25L * (attempt + 1)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (persisted) {
            long current = ++sequence;
            lastPersistedEntry = entry;
            written.incrementAndGet();
            lastError = "";
            if (current % checkpointInterval == 0) {
                try { appendCheckpoint(current, entry); }
                catch (IOException checkpointError) {
                    failedWrites.incrementAndGet();
                    lastError = "audit checkpoint write failed: " + checkpointError.getMessage();
                    plugin.getLogger().severe(lastError);
                }
            }
            return;
        }
        failedWrites.incrementAndGet();
        integrityValid = false;
        activeLogWritable = false;
        failClosed.set(true);
        lastError = failure == null ? "unknown audit I/O error" : failure.getMessage();
        emergencyAppend(entry, lastError);
        plugin.getLogger().severe("Failed to write audit.log; entry copied to audit-recovery.log: " + lastError);
    }

    private void emergencyAppend(AuditEntry entry, String reason) {
        try {
            Files.createDirectories(recoveryFile.getParent());
            Files.writeString(recoveryFile, serializeLine(entry) + "|RECOVERY=" + encode(reason) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            recoveredWrites.incrementAndGet();
        } catch (IOException recoveryError) {
            lastError = "audit and recovery writes failed: " + recoveryError.getMessage();
            plugin.getLogger().severe(lastError);
        }
    }

    private void loadAndVerify() {
        if (!Files.exists(logFile)) return;
        ChainVerification verification;
        try {
            verification = verifyFile(logFile);
            if (verification.valid()) verifyCheckpoints(logFile, checkpointFile);
        } catch (Exception error) {
            verification = new ChainVerification(false, 0, error.getMessage(), "GENESIS", 0);
        }
        if (!verification.valid()) {
            failIntegrity(verification.line(), verification.reason());
            quarantineCorruptSegment();
            return;
        }
        sequence = verification.entries();
        lastHash = verification.lastHash();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    AuditEntry parsed = parse(line.split("\\|", -1));
                    addRecent(parsed);
                    lastPersistedEntry = parsed;
                }
                catch (Exception ignored) { }
            }
        } catch (IOException error) {
            failIntegrity(0, error.getMessage());
            quarantineCorruptSegment();
        }
    }

    static ChainVerification verifyFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return new ChainVerification(true, 0, "", "GENESIS", 0);
        String expectedPrevious = "GENESIS";
        long entries = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length != 11) return new ChainVerification(false, lineNumber, "invalid field count", expectedPrevious, entries);
                String payload = String.join("|", java.util.Arrays.copyOf(fields, 10));
                if (!constantTimeEquals(sha256(payload), fields[10]) || !expectedPrevious.equals(fields[9]))
                    return new ChainVerification(false, lineNumber, "SHA-256 chain mismatch", expectedPrevious, entries);
                try { parseStatic(fields); }
                catch (Exception error) { return new ChainVerification(false, lineNumber, "invalid entry: " + error.getMessage(), expectedPrevious, entries); }
                expectedPrevious = fields[10];
                entries++;
            }
        }
        return new ChainVerification(true, 0, "", expectedPrevious, entries);
    }

    private void verifyCheckpoints(Path audit, Path checkpoint) throws IOException {
        if (!Files.isRegularFile(checkpoint)) return; // Compatible upgrade from the original SHA-only journal.
        Map<Long, String[]> expected = new HashMap<>();
        for (String line : Files.readAllLines(checkpoint, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\|", -1);
            if (fields.length != 4) throw new IOException("invalid audit checkpoint format");
            long number = Long.parseLong(fields[0]);
            String payload = fields[0] + "|" + fields[1] + "|" + fields[2];
            if (!constantTimeEquals(hmac(payload), fields[3])) throw new IOException("audit checkpoint HMAC mismatch");
            expected.put(number, fields);
        }
        if (expected.isEmpty()) return;
        long number = 0;
        try (BufferedReader reader = Files.newBufferedReader(audit, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                number++;
                String[] checkpointFields = expected.remove(number);
                if (checkpointFields == null) continue;
                String[] auditFields = line.split("\\|", -1);
                if (auditFields.length != 11 || !checkpointFields[1].equals(auditFields[1])
                        || !checkpointFields[2].equals(auditFields[10])) throw new IOException("audit checkpoint does not match journal");
            }
        }
        if (!expected.isEmpty()) throw new IOException("audit checkpoint points past end of journal");
    }

    private void appendCheckpoint(long number, AuditEntry entry) throws IOException {
        String payload = number + "|" + entry.id() + "|" + entry.hash();
        YamlPersistenceService.atomicWrite(checkpointFile,
                (payload + "|" + hmac(payload) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                logFile.getParent());
        checkpoints.incrementAndGet();
    }

    private void quarantineCorruptSegment() {
        String suffix = SEGMENT_TIME.format(Instant.now());
        try {
            if (Files.exists(logFile)) Files.move(logFile, logFile.resolveSibling("audit-tampered-" + suffix + ".log"));
            if (Files.exists(checkpointFile)) Files.move(checkpointFile,
                    checkpointFile.resolveSibling("audit-tampered-" + suffix + ".checkpoints"));
            lastHash = "GENESIS";
            sequence = 0;
        } catch (IOException error) {
            lastError = "could not quarantine damaged audit segment: " + error.getMessage();
            plugin.getLogger().severe(lastError);
            accepting.set(false);
            failClosed.set(true);
        }
    }

    private void failIntegrity(int line, String reason) {
        integrityValid = false;
        failClosed.set(true);
        lastError = "integrity failure at line " + line + ": " + reason;
        plugin.getLogger().severe("audit.log integrity failure at line " + line + ": " + reason);
    }

    private AuditEntry parse(String[] fields) { return parseStatic(fields); }

    private static AuditEntry parseStatic(String[] fields) {
        return new AuditEntry(UUID.fromString(fields[1]), Long.parseLong(fields[2]), parseUuid(fields[3]),
                decode(fields[4]), decode(fields[5]), parseUuid(fields[6]), decode(fields[7]), decode(fields[8]),
                fields[9], fields[10]);
    }

    private static String serializePayload(AuditEntry entry) {
        return String.join("|", "1", entry.id().toString(), Long.toString(entry.timestamp()),
                uuid(entry.actorUuid()), encode(entry.actorName()), encode(entry.action()), uuid(entry.targetUuid()),
                encode(entry.targetName()), encode(entry.details()), entry.previousHash());
    }

    private static String serializeLine(AuditEntry entry) { return serializePayload(entry) + "|" + entry.hash(); }

    private synchronized void addRecent(AuditEntry entry) {
        recent.addLast(entry);
        while (recent.size() > memoryLimit) recent.removeFirst();
    }

    public synchronized List<AuditEntry> getRecent() {
        List<AuditEntry> result = new ArrayList<>(recent);
        Collections.reverse(result);
        return List.copyOf(result);
    }

    /** Reads matching persisted entries after flushing the async writer. */
    public List<AuditEntry> findForExport(UUID targetUuid, int caseId, int limit) {
        flush();
        int safeLimit = Math.clamp(limit, 100, 50_000);
        if (!Files.isRegularFile(logFile)) return List.of();
        Deque<AuditEntry> matches = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length != 11) continue;
                try {
                    AuditEntry entry = parse(fields);
                    if ((targetUuid != null && targetUuid.equals(entry.targetUuid()))
                            || entry.details().contains("case=" + caseId)) {
                        matches.addLast(entry);
                        while (matches.size() > safeLimit) matches.removeFirst();
                    }
                } catch (Exception ignored) { }
            }
        } catch (IOException error) {
            plugin.getLogger().warning("Could not read full audit for case #" + caseId + ": " + error.getMessage());
        }
        return List.copyOf(matches);
    }

    public boolean flush() {
        long timeout = Math.clamp(plugin.getConfig().getLong("audit.flush-timeout-ms", 5000L), 500L, 30_000L);
        long target = enqueued.get();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        while (processed.get() < target && System.nanoTime() < deadline) {
            try { Thread.sleep(5L); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        }
        return processed.get() >= target;
    }

    @Override
    public void close() {
        if (!accepting.compareAndSet(true, false)) return;
        flush();
        writerRunning.set(false);
        writerThread.interrupt();
        try { writerThread.join(5000L); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        AuditEntry tail = lastPersistedEntry;
        if (tail != null && sequence % checkpointInterval != 0) {
            try { appendCheckpoint(sequence, tail); }
            catch (IOException error) { failedWrites.incrementAndGet(); lastError = error.getMessage(); }
        }
    }

    public boolean isIntegrityValid() { return integrityValid; }

    /** Security preflight: false after any unjournaled event or integrity/I/O failure. */
    public boolean isAvailableForCriticalActions() {
        return accepting.get() && writerThread.isAlive() && integrityValid && activeLogWritable && !failClosed.get();
    }

    public Health health() {
        return new Health(ingress.size(), ingress.capacity(), writerThread.isAlive(),
                writing.get(), written.get(), failedWrites.get(), recoveredWrites.get(), checkpoints.get(),
                ingress.rejected(), failClosed.get(), lastError);
    }

    private byte[] loadOrCreateKey(Path keyFile, Path root) {
        try {
            if (Files.isRegularFile(keyFile)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.US_ASCII).trim());
                if (decoded.length >= 32) return decoded;
                throw new IOException("audit.key is too short");
            }
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            YamlPersistenceService.atomicWrite(keyFile, Base64.getEncoder().encode(generated), root);
            return generated;
        } catch (Exception error) {
            integrityValid = false;
            lastError = "could not load audit HMAC key: " + error.getMessage();
            plugin.getLogger().severe(lastError);
            byte[] ephemeral = new byte[32];
            new SecureRandom().nextBytes(ephemeral);
            return ephemeral;
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("HmacSHA256 unavailable", error); }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable", error); }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }
    private static String encode(String value) { return ENCODER.encodeToString(safe(value).getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { return new String(DECODER.decode(value), StandardCharsets.UTF_8); }
    private static String uuid(UUID value) { return value == null ? "" : value.toString(); }
    private static UUID parseUuid(String value) { return value.isBlank() ? null : UUID.fromString(value); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String bounded(String value, int maximum) {
        String safe = safe(value);
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
