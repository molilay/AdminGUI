package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Creates self-verifying rotating snapshots of YAML state and audit.log. Database files are excluded. */
public final class YamlBackupManager {

    public record BackupResult(Path file, int entries, long bytes, long generation) {
        public BackupResult(Path file, int entries, long bytes) { this(file, entries, bytes, -1L); }
    }
    public record RestoreStageResult(Path archive, Path emergencyBackup, int yamlFiles) {}
    public record BackupVerification(boolean valid, int entries, long bytes, List<String> errors) {}
    public record RestorePreview(Path archive, boolean valid, int creates, int replaces,
                                 List<String> files, List<String> errors) {}

    private static final String MANIFEST = "manifest.sha256";
    private static final String MANIFEST_SIGNATURE = "manifest.hmac";
    private static final long MAX_YAML_BYTES = 4L * 1024 * 1024;
    private static final long MAX_STAGE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_VERIFY_ENTRY_BYTES = 256L * 1024 * 1024;
    private static final long MAX_VERIFY_TOTAL_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1000;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);
    private final AdvancedModeratorGUI plugin;
    private final ArtifactSignatureService signatures;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile BukkitTask scheduledTask;
    private volatile long lastBackupAt;
    private volatile BackupVerification lastVerification;

    public YamlBackupManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.signatures = new ArtifactSignatureService(plugin);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("backups.enabled", true)) return;
        long hours = Math.clamp(plugin.getConfig().getLong("backups.interval-hours", 24), 1, 168);
        long period = hours * 60L * 60L * 20L;
        long initial = plugin.getConfig().getBoolean("backups.create-on-start", true) ? 100L : period;
        scheduledTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            createAsync().whenComplete((ignored, error) -> {
                if (error != null) plugin.getLogger().warning("Could not create YAML backup: " + rootMessage(error));
            });
        }, initial, period);
    }

    public void stop() {
        BukkitTask task = scheduledTask;
        if (task != null) task.cancel();
        scheduledTask = null;
    }

    public void restart() { stop(); start(); }

    public CompletableFuture<BackupResult> createAsync() {
        return plugin.getTaskExecutor().supply("yaml-backup", () -> {
            try { return createNow(); }
            catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }

    public CompletableFuture<RestoreStageResult> stageRestoreAsync(String fileName) {
        return plugin.getTaskExecutor().supply("yaml-restore-stage", () -> {
            try {
                Path archive = resolveBackup(fileName);
                BackupVerification verification = verifyArchiveForRestore(archive);
                lastVerification = verification;
                if (!verification.valid()) throw new IOException("Backup verification failed: " + String.join("; ", verification.errors()));
                Path protectedCopy = archive.resolveSibling("restore-source-" + java.util.UUID.randomUUID() + ".tmp");
                Files.copy(archive, protectedCopy);
                try {
                    BackupResult emergency = createNow();
                    int restored = stageArchive(protectedCopy, archive.getFileName().toString(), emergency.file());
                    return new RestoreStageResult(archive, emergency.file(), restored);
                } finally {
                    Files.deleteIfExists(protectedCopy);
                }
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    /** Read-only restore preview: validates hashes/YAML and reports which files would be created or replaced. */
    public RestorePreview previewRestore(String fileName) {
        try {
            Path archive = resolveBackup(fileName);
            BackupVerification verification = verifyArchiveForRestore(archive);
            lastVerification = verification;
            if (!verification.valid()) return new RestorePreview(archive, false, 0, 0, List.of(), verification.errors());
            Path root = dataRoot();
            List<String> files = new ArrayList<>();
            int creates = 0;
            int replaces = 0;
            try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || MANIFEST.equals(entry.getName()) || MANIFEST_SIGNATURE.equals(entry.getName())) continue;
                    Path relative = safeRelative(entry.getName());
                    if (!isYaml(relative)) continue;
                    byte[] bytes = readLimited(zip.getInputStream(entry), MAX_YAML_BYTES);
                    validateYaml(bytes, relative.getFileName().toString());
                    files.add(relative.toString().replace('\\', '/'));
                    if (Files.exists(root.resolve(relative))) replaces++; else creates++;
                }
            }
            return new RestorePreview(archive, true, creates, replaces, List.copyOf(files), List.of());
        } catch (Exception error) {
            return new RestorePreview(null, false, 0, 0, List.of(), List.of(error.getMessage()));
        }
    }

    /** Cancels a staged restore only before application has started. */
    public boolean cancelPendingRestore() throws IOException {
        Path root = dataRoot();
        Path marker = root.resolve(".pending-yaml-restore");
        Path stage = root.resolve("pending-restore");
        if (!Files.exists(marker) && !Files.exists(stage)) return false;
        RestoreJournal journal = Files.isRegularFile(marker) ? readJournal(marker) : null;
        if (journal != null && !"STAGED".equals(journal.state()))
            throw new IOException("Restore is already applying and cannot be cancelled");
        Files.deleteIfExists(marker);
        deleteTree(stage, root);
        deleteTree(root.resolve("pending-restore-rollback"), root);
        return true;
    }

    private int stageArchive(Path archive, String sourceName, Path emergencyBackup) throws IOException {
        Path root = dataRoot();
        Path stage = root.resolve("pending-restore");
        Path marker = root.resolve(".pending-yaml-restore");
        if (Files.exists(stage) || Files.exists(marker)) throw new IOException("A pending restore already exists");
        Files.createDirectories(stage);
        int count = 0;
        long total = 0;
        List<String> files = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || MANIFEST.equals(entry.getName()) || MANIFEST_SIGNATURE.equals(entry.getName())) continue;
                Path relative = safeRelative(entry.getName());
                if (!isYaml(relative)) continue;
                if (++count > MAX_ENTRIES) throw new IOException("Archive exceeds safe entry limit");
                byte[] bytes = readLimited(zip.getInputStream(entry), MAX_YAML_BYTES);
                total += bytes.length;
                if (total > MAX_STAGE_BYTES) throw new IOException("Archive exceeds safe restore size");
                validateYaml(bytes, relative.getFileName().toString());
                Path output = stage.resolve(relative).normalize();
                if (!output.startsWith(stage)) throw new IOException("Unsafe ZIP path: " + entry.getName());
                YamlPersistenceService.atomicWrite(output, bytes, root);
                String portable = relative.toString().replace('\\', '/');
                files.add(portable);
                hashes.put(portable, sha256(bytes));
                if (Files.exists(root.resolve(relative))) existing.add(portable);
            }
        } catch (Exception error) {
            deleteTree(stage, root);
            if (error instanceof IOException io) throw io;
            throw new IOException(error.getMessage(), error);
        }
        if (count == 0) {
            deleteTree(stage, root);
            throw new IOException("Backup contains no YAML files");
        }
        writeJournal(marker, new RestoreJournal("STAGED", sourceName,
                root.relativize(emergencyBackup.toAbsolutePath().normalize()).toString().replace('\\', '/'),
                files, existing, hashes), root);
        return count;
    }

    /** Applies a validated restore before managers load. Interrupted transactions are rolled back on next start. */
    public static boolean applyPendingRestore(AdvancedModeratorGUI plugin) {
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path marker = root.resolve(".pending-yaml-restore");
        Path stage = root.resolve("pending-restore");
        Path rollback = root.resolve("pending-restore-rollback");
        if (!Files.isRegularFile(marker)) return false;
        try {
            RestoreJournal journal = readJournal(marker);
            if ("COMMITTED".equals(journal.state())) {
                cleanupTransaction(root, stage, rollback, marker);
                plugin.getLogger().warning("Completed cleanup for committed YAML restore from " + journal.source());
                return true;
            }
            if (!"STAGED".equals(journal.state())) {
                rollback(journal, root, rollback, marker);
                cleanupTransaction(root, stage, rollback, marker);
                plugin.getLogger().severe("Interrupted YAML restore was rolled back automatically");
                return false;
            }
            if (!Files.isDirectory(stage)) throw new IOException("Pending restore staging directory is missing");
            deleteTree(rollback, root);
            Files.createDirectories(rollback);
            Set<String> existing = new HashSet<>(journal.existing());
            // Prepare a complete rollback set before changing journal state or live files.
            for (String name : journal.files()) {
                Path relative = safeRelative(name);
                Path source = stage.resolve(relative).normalize();
                if (!Files.isRegularFile(source)) throw new IOException("Missing staged file: " + name);
                byte[] stagedBytes = Files.readAllBytes(source);
                validateYaml(stagedBytes, relative.getFileName().toString());
                if (!constantTimeEquals(journal.hashes().get(name), sha256(stagedBytes)))
                    throw new IOException("Staged file hash mismatch: " + name);
                if (existing.contains(name)) {
                    Path live = root.resolve(relative).normalize();
                    if (!Files.isRegularFile(live)) throw new IOException("Live file changed since preview: " + name);
                    Path saved = rollback.resolve(relative).normalize();
                    Files.createDirectories(saved.getParent());
                    Files.copy(live, saved, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            RestoreJournal applying = journal.withState("APPLYING");
            writeJournal(marker, applying, root);
            try {
                for (String name : applying.files()) {
                    Path relative = safeRelative(name);
                    byte[] bytes = Files.readAllBytes(stage.resolve(relative));
                    if (!constantTimeEquals(applying.hashes().get(name), sha256(bytes)))
                        throw new IOException("Staged file changed during restore: " + name);
                    YamlPersistenceService.atomicWrite(root.resolve(relative), bytes, root);
                }
                writeJournal(marker, applying.withState("COMMITTED"), root);
                cleanupTransaction(root, stage, rollback, marker);
                plugin.getLogger().warning("Applied verified transactional YAML restore from " + applying.source());
                return true;
            } catch (Exception applyError) {
                rollback(applying, root, rollback, marker);
                cleanupTransaction(root, stage, rollback, marker);
                throw new IOException("Restore failed and was rolled back: " + applyError.getMessage(), applyError);
            }
        } catch (Exception error) {
            plugin.getLogger().severe("Could not apply pending YAML restore: " + error.getMessage());
            return false;
        }
    }

    private static void rollback(RestoreJournal journal, Path root, Path rollback, Path marker) throws IOException {
        writeJournal(marker, journal.withState("ROLLING_BACK"), root);
        Set<String> existing = new HashSet<>(journal.existing());
        for (String name : journal.files()) {
            Path relative = safeRelative(name);
            Path live = root.resolve(relative).normalize();
            if (existing.contains(name)) {
                Path saved = rollback.resolve(relative).normalize();
                if (!Files.isRegularFile(saved)) throw new IOException("Rollback copy is missing: " + name);
                YamlPersistenceService.atomicWrite(live, Files.readAllBytes(saved), root);
            } else Files.deleteIfExists(live);
        }
    }

    private static void cleanupTransaction(Path root, Path stage, Path rollback, Path marker) throws IOException {
        Files.deleteIfExists(marker);
        deleteTree(stage, root);
        deleteTree(rollback, root);
    }

    public BackupResult createNow() throws IOException {
        if (!running.compareAndSet(false, true)) throw new IOException("Backup creation is already running");
        try {
            if (!plugin.getAuditManager().flush()) throw new IOException("Could not flush audit before snapshot");
            Path dataRoot = dataRoot();
            Path backupDir = dataRoot.resolve("backups");
            Files.createDirectories(backupDir);
            String baseName = "amgui-yaml-" + FILE_TIME.format(Instant.now());
            Path temporary = backupDir.resolve(baseName + ".tmp");
            Path destination = backupDir.resolve(baseName + ".zip");
            Path staging = backupDir.resolve(".snapshot-" + java.util.UUID.randomUUID());
            int entries;
            long generation;
            try (YamlPersistenceService.GenerationSnapshot snapshot = YamlPersistenceService.forPlugin(plugin)
                    .captureSnapshot(staging, 4, YamlBackupManager::shouldInclude, 20)) {
                if (snapshot.files().isEmpty()) throw new IOException("No YAML files found for backup");
                generation = snapshot.generation();
                entries = 0;
                StringBuilder manifest = new StringBuilder("# AdvancedModeratorGUI SHA-256 manifest v1 generation="
                        + generation + "\n");
                try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                     ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                    zip.setLevel(6);
                    byte[] buffer = new byte[16 * 1024];
                    for (YamlPersistenceService.SnapshotFile input : snapshot.files()) {
                        String entryName = input.relative().toString().replace('\\', '/');
                        safeRelative(entryName);
                        MessageDigest digest = sha256Digest();
                        ZipEntry entry = new ZipEntry(entryName);
                        entry.setTime(input.lastModifiedMillis());
                        zip.putNextEntry(entry);
                        try (InputStream stream = Files.newInputStream(input.capturedFile())) {
                            int read;
                            while ((read = stream.read(buffer)) >= 0) {
                                zip.write(buffer, 0, read);
                                digest.update(buffer, 0, read);
                            }
                        }
                        zip.closeEntry();
                        manifest.append(java.util.HexFormat.of().formatHex(digest.digest())).append("  ").append(entryName).append('\n');
                        entries++;
                    }
                    byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
                    zip.putNextEntry(new ZipEntry(MANIFEST));
                    zip.write(manifestBytes);
                    zip.closeEntry();
                    zip.putNextEntry(new ZipEntry(MANIFEST_SIGNATURE));
                    zip.write(signatures.envelope(manifestBytes).getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            } catch (Exception error) {
                Files.deleteIfExists(temporary);
                try { if (Files.exists(staging)) deleteTree(staging, dataRoot); } catch (Exception ignored) {}
                throw error;
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
            BackupVerification verification = verifyArchiveForRestore(destination);
            lastVerification = verification;
            if (!verification.valid()) {
                Files.deleteIfExists(destination);
                throw new IOException("Created backup failed self-verification: " + String.join("; ", verification.errors()));
            }
            lastBackupAt = System.currentTimeMillis();
            rotate(backupDir);
            return new BackupResult(destination, entries, Files.size(destination), generation);
        } finally { running.set(false); }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public BackupVerification verifyBackup(String fileName) {
        try {
            BackupVerification result = verifyArchiveForRestore(resolveBackup(fileName));
            lastVerification = result;
            return result;
        } catch (Exception error) {
            BackupVerification result = new BackupVerification(false, 0, 0, List.of(error.getMessage()));
            lastVerification = result;
            return result;
        }
    }

    static BackupVerification verifyArchive(Path archive) {
        return verifyArchive(archive, null, false);
    }

    private BackupVerification verifyArchiveForRestore(Path archive) {
        return verifyArchive(archive, signatures, plugin.getConfig().getBoolean("backups.require-signature", true));
    }

    private static BackupVerification verifyArchive(Path archive, ArtifactSignatureService signatures, boolean requireSignature) {
        List<String> errors = new ArrayList<>();
        int verified = 0;
        long total = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Map<String, ZipEntry> entries = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) continue;
                String name = safeRelative(entry.getName()).toString().replace('\\', '/');
                if (entries.putIfAbsent(name, entry) != null) throw new IOException("Duplicate ZIP entry: " + name);
                if (entries.size() > MAX_ENTRIES + 2) throw new IOException("Archive exceeds safe entry limit");
            }
            ZipEntry manifestEntry = entries.remove(MANIFEST);
            if (manifestEntry == null) throw new IOException("SHA-256 manifest is missing");
            byte[] manifestBytes = readLimited(zip.getInputStream(manifestEntry), 1024L * 1024);
            ZipEntry signatureEntry = entries.remove(MANIFEST_SIGNATURE);
            if (signatureEntry == null) {
                if (requireSignature) throw new IOException("HMAC manifest signature is missing");
            } else if (signatures != null) {
                String envelope = new String(readLimited(zip.getInputStream(signatureEntry), 4096), StandardCharsets.UTF_8);
                if (!signatures.verifyEnvelope(manifestBytes, envelope)) throw new IOException("HMAC manifest signature mismatch");
            }
            String manifestText = new String(manifestBytes, StandardCharsets.UTF_8);
            Map<String, String> manifest = parseManifest(manifestText);
            if (!manifest.keySet().equals(entries.keySet())) throw new IOException("Manifest file list does not match ZIP entries");
            for (Map.Entry<String, ZipEntry> item : entries.entrySet()) {
                HashedStream hashed = digestLimited(zip.getInputStream(item.getValue()), MAX_VERIFY_ENTRY_BYTES);
                total += hashed.bytes();
                if (total > MAX_VERIFY_TOTAL_BYTES) throw new IOException("Archive exceeds verification size limit");
                if (!constantTimeEquals(manifest.get(item.getKey()), hashed.sha256())) errors.add("Hash mismatch: " + item.getKey());
                verified++;
            }
        } catch (Exception error) { errors.add(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()); }
        return new BackupVerification(errors.isEmpty(), verified, total, List.copyOf(errors));
    }

    private static Map<String, String> parseManifest(String value) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : value.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.matches("[0-9a-fA-F]{64}  .+")) throw new IOException("Invalid manifest line");
            String hash = line.substring(0, 64).toLowerCase(Locale.ROOT);
            String name = safeRelative(line.substring(66)).toString().replace('\\', '/');
            if (MANIFEST.equals(name) || MANIFEST_SIGNATURE.equals(name) || result.putIfAbsent(name, hash) != null)
                throw new IOException("Invalid manifest entry: " + name);
        }
        if (result.isEmpty()) throw new IOException("Manifest is empty");
        return result;
    }

    static boolean shouldInclude(Path relative) {
        String normalized = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("exports/") || normalized.startsWith("backups/")
                || normalized.startsWith("pending-restore/") || normalized.startsWith("pending-restore-rollback/")
                || normalized.startsWith("case-exports/")) return false;
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml") || name.equals("audit.log")
                || name.equals("audit.checkpoints");
    }

    private void rotate(Path backupDir) throws IOException {
        int keep = Math.clamp(plugin.getConfig().getInt("backups.keep-last", 14), 1, 100);
        List<Path> backups;
        try (Stream<Path> files = Files.list(backupDir)) {
            backups = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("amgui-yaml-\\d{8}-\\d{6}-\\d{3}\\.zip"))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed()).toList();
        }
        for (int index = keep; index < backups.size(); index++) Files.deleteIfExists(backups.get(index));
    }

    public List<Path> listBackups() {
        Path directory = dataRoot().resolve("backups");
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed()).toList();
        } catch (IOException ignored) { return List.of(); }
    }

    private Path resolveBackup(String fileName) throws IOException {
        if (fileName == null || !fileName.equals(Path.of(fileName).getFileName().toString())) throw new IOException("Specify an exact backup filename");
        return listBackups().stream().filter(path -> path.getFileName().toString().equals(fileName)).findFirst()
                .orElseThrow(() -> new IOException("Backup not found"));
    }

    private Path dataRoot() { return plugin.getDataFolder().toPath().toAbsolutePath().normalize(); }
    private long lastModified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException ignored) { return 0L; } }
    public long getLastBackupAt() { return lastBackupAt; }
    public boolean isRunning() { return running.get(); }
    public BackupVerification getLastVerification() { return lastVerification; }

    private record RestoreJournal(String state, String source, String emergency, List<String> files,
                                  List<String> existing, Map<String, String> hashes) {
        RestoreJournal withState(String value) { return new RestoreJournal(value, source, emergency, files, existing, hashes); }
    }

    private static void writeJournal(Path marker, RestoreJournal journal, Path root) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "2");
        properties.setProperty("state", journal.state());
        properties.setProperty("source", encodeJournal(journal.source()));
        properties.setProperty("emergency", encodeJournal(journal.emergency()));
        properties.setProperty("files", journal.files().stream().map(YamlBackupManager::encodeJournal).collect(java.util.stream.Collectors.joining(",")));
        properties.setProperty("existing", journal.existing().stream().map(YamlBackupManager::encodeJournal).collect(java.util.stream.Collectors.joining(",")));
        properties.setProperty("hashes", journal.hashes().entrySet().stream()
                .map(entry -> encodeJournal(entry.getKey()) + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",")));
        StringWriter writer = new StringWriter();
        properties.store(writer, "AdvancedModeratorGUI transactional restore journal");
        YamlPersistenceService.atomicWrite(marker, writer.toString().getBytes(StandardCharsets.UTF_8), root);
    }

    private static RestoreJournal readJournal(Path marker) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(marker, StandardCharsets.UTF_8)));
        if (!"2".equals(properties.getProperty("version"))) throw new IOException("Unsupported restore journal version");
        return new RestoreJournal(properties.getProperty("state", ""), decodeJournal(properties.getProperty("source", "")),
                decodeJournal(properties.getProperty("emergency", "")), decodeList(properties.getProperty("files", "")),
                decodeList(properties.getProperty("existing", "")), decodeHashes(properties.getProperty("hashes", "")));
    }

    private static String encodeJournal(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decodeJournal(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static List<String> decodeList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",", -1)).map(YamlBackupManager::decodeJournal).toList();
    }

    private static Map<String, String> decodeHashes(String value) throws IOException {
        if (value == null || value.isBlank()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : value.split(",", -1)) {
            int separator = item.lastIndexOf(':');
            if (separator <= 0 || !item.substring(separator + 1).matches("[0-9a-fA-F]{64}"))
                throw new IOException("Invalid staged restore hash journal");
            String name = decodeJournal(item.substring(0, separator));
            if (result.putIfAbsent(name, item.substring(separator + 1).toLowerCase(Locale.ROOT)) != null)
                throw new IOException("Duplicate staged restore hash: " + name);
        }
        return Map.copyOf(result);
    }

    private static Path safeRelative(String raw) throws IOException {
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0) throw new IOException("Unsafe empty ZIP path");
        String portable = raw.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) throw new IOException("Absolute ZIP path: " + raw);
        Path relative;
        try { relative = Path.of(portable).normalize(); }
        catch (Exception error) { throw new IOException("Invalid ZIP path: " + raw, error); }
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) throw new IOException("Unsafe ZIP path: " + raw);
        return relative;
    }

    private static boolean isYaml(Path relative) {
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IOException("Uncompressed ZIP entry exceeds safe limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private record HashedStream(String sha256, long bytes) {}

    private static HashedStream digestLimited(InputStream input, long limit) throws IOException {
        try (input) {
            MessageDigest digest = sha256Digest();
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IOException("Uncompressed ZIP entry exceeds verification limit");
                digest.update(buffer, 0, read);
            }
            return new HashedStream(java.util.HexFormat.of().formatHex(digest.digest()), total);
        }
    }

    private static void validateYaml(byte[] bytes, String name) throws IOException {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IOException("Invalid YAML " + name + ": " + error.getMessage(), error); }
    }

    private static void deleteTree(Path target, Path root) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        Path safeRoot = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(safeRoot) || normalized.equals(safeRoot)) throw new IOException("Refusing unsafe recursive delete");
        if (!Files.exists(normalized)) return;
        try (Stream<Path> walk = Files.walk(normalized)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static MessageDigest sha256Digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static String sha256(byte[] bytes) { return java.util.HexFormat.of().formatHex(sha256Digest().digest(bytes)); }
    private static boolean constantTimeEquals(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }
}
