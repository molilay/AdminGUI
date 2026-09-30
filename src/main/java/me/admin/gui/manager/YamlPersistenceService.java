package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * A single, bounded and debounced writer for small YAML state files.
 * Data is serialized by the caller before it crosses the thread boundary, then
 * written through a forced temporary file and an atomic replace where supported.
 */
public final class YamlPersistenceService implements AutoCloseable {

    public record Health(int pendingFiles, int capacity, boolean closed, long completedWrites,
                         long coalescedWrites, long failedWrites, long rejectedWrites,
                         long lastWriteDurationMillis, String lastError) {}

    private record Pending(String contents, ScheduledFuture<?> future) {}

    public record SnapshotFile(Path relative, Path capturedFile, long lastModifiedMillis, long bytes) {}

    /** A stable filesystem generation copied to a private staging directory. */
    public static final class GenerationSnapshot implements AutoCloseable {
        private final long generation;
        private final Path directory;
        private final List<SnapshotFile> files;

        private GenerationSnapshot(long generation, Path directory, List<SnapshotFile> files) {
            this.generation = generation;
            this.directory = directory;
            this.files = List.copyOf(files);
        }

        public long generation() { return generation; }
        public Path directory() { return directory; }
        public List<SnapshotFile> files() { return files; }

        @Override public void close() throws IOException { deleteSnapshotTree(directory); }
    }

    @FunctionalInterface
    interface IoAction { void run() throws IOException; }

    private static final class GenerationTracker {
        private final AtomicLong generation = new AtomicLong();
        private final AtomicInteger activeWriters = new AtomicInteger();
    }

    private record GenerationStamp(long generation, int activeWriters) {}

    private static final Map<AdvancedModeratorGUI, YamlPersistenceService> INSTANCES = new IdentityHashMap<>();
    private static final Map<Path, GenerationTracker> GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private final Path dataRoot;
    private final Logger logger;
    private final int capacity;
    private final long debounceMillis;
    private final long flushTimeoutMillis;
    private final ScheduledExecutorService executor;
    private final Map<Path, Pending> pending = new java.util.HashMap<>();
    private final AtomicLong completedWrites = new AtomicLong();
    private final AtomicLong coalescedWrites = new AtomicLong();
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong rejectedWrites = new AtomicLong();
    private volatile long lastWriteDurationMillis;
    private volatile String lastError = "";
    private volatile boolean closed;

    private YamlPersistenceService(Path dataRoot, Logger logger, int capacity, long debounceMillis, long flushTimeoutMillis) {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
        this.logger = logger;
        this.capacity = Math.clamp(capacity, 8, 1024);
        this.debounceMillis = Math.clamp(debounceMillis, 0L, 10_000L);
        this.flushTimeoutMillis = Math.clamp(flushTimeoutMillis, 100L, 30_000L);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "amgui-yaml-writer");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) -> logger.severe("YAML writer crashed: " + error.getMessage()));
            return thread;
        };
        var scheduled = (java.util.concurrent.ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, factory);
        scheduled.setRemoveOnCancelPolicy(true);
        scheduled.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.executor = scheduled;
    }

    public static synchronized YamlPersistenceService forPlugin(AdvancedModeratorGUI plugin) {
        return INSTANCES.computeIfAbsent(plugin, value -> new YamlPersistenceService(
                plugin.getDataFolder().toPath(), plugin.getLogger(),
                plugin.getConfig().getInt("persistence.queue-capacity", 128),
                plugin.getConfig().getLong("persistence.debounce-ms", 250L),
                plugin.getConfig().getLong("persistence.flush-timeout-ms", 5000L)));
    }

    /** Flushes and disposes the plugin-wide writer. Intended for onDisable. */
    public static synchronized void closeFor(AdvancedModeratorGUI plugin) {
        YamlPersistenceService service = INSTANCES.remove(plugin);
        if (service != null) service.close();
    }

    /** Shared adapter used by managers so Bukkit's synchronous FileConfiguration.save is never needed. */
    public static boolean queueYaml(AdvancedModeratorGUI plugin, java.io.File target,
                                    YamlConfiguration configuration, String label) {
        boolean accepted = forPlugin(plugin).save(target.toPath(), configuration.saveToString());
        if (!accepted) plugin.getLogger().severe("YAML snapshot rejected (" + label + "): " + target.getName());
        return accepted;
    }

    /** Atomic immediate variant for read-modify-write files that do not keep an in-memory source of truth. */
    public static boolean saveYamlNow(AdvancedModeratorGUI plugin, java.io.File target,
                                      YamlConfiguration configuration, String label) {
        try {
            atomicWrite(target.toPath(), configuration.saveToString().getBytes(StandardCharsets.UTF_8),
                    plugin.getDataFolder().toPath());
            return true;
        } catch (IOException error) {
            plugin.getLogger().severe("Atomic YAML write failed (" + label + "): " + error.getMessage());
            return false;
        }
    }

    /** Enqueues the latest immutable YAML snapshot. Repeated saves for one file are coalesced. */
    public synchronized boolean save(Path target, String contents) {
        if (closed) {
            rejectedWrites.incrementAndGet();
            return false;
        }
        Path safeTarget;
        try {
            safeTarget = requireInsideRoot(target);
        } catch (IOException error) {
            recordFailure(error);
            rejectedWrites.incrementAndGet();
            return false;
        }
        Pending old = pending.get(safeTarget);
        if (old == null && pending.size() >= capacity) {
            rejectedWrites.incrementAndGet();
            lastError = "YAML persistence queue capacity exceeded";
            logger.severe(lastError + ": " + safeTarget.getFileName());
            return false;
        }
        if (old != null) {
            old.future().cancel(false);
            coalescedWrites.incrementAndGet();
        }
        ScheduledFuture<?> future = executor.schedule(() -> writePending(safeTarget), debounceMillis, TimeUnit.MILLISECONDS);
        pending.put(safeTarget, new Pending(contents == null ? "" : contents, future));
        return true;
    }

    private void writePending(Path target) {
        String contents;
        synchronized (this) {
            Pending item = pending.remove(target);
            if (item == null) return;
            contents = item.contents();
        }
        writeNow(target, contents);
    }

    private void writeNow(Path target, String contents) {
        long started = System.nanoTime();
        try {
            atomicWrite(target, contents.getBytes(StandardCharsets.UTF_8), dataRoot);
            completedWrites.incrementAndGet();
            lastError = "";
        } catch (IOException error) {
            recordFailure(error);
        } finally {
            lastWriteDurationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        }
    }

    /** Writes every queued latest snapshot before returning. */
    public void flush() { flush(flushTimeoutMillis); }

    /** Returns false if the bounded wait elapsed; queued writes remain owned by the writer. */
    public boolean flush(long timeoutMillis) {
        List<Map.Entry<Path, Pending>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(pending.entrySet());
            pending.clear();
            snapshot.forEach(entry -> entry.getValue().future().cancel(false));
        }
        snapshot.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        try {
            var barrier = executor.submit(() -> snapshot.forEach(entry -> writeNow(entry.getKey(), entry.getValue().contents())));
            barrier.get(Math.clamp(timeoutMillis, 100L, 30_000L), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | java.util.concurrent.RejectedExecutionException error) {
            lastError = "YAML flush failed: " + error.getMessage();
            failedWrites.incrementAndGet();
            logger.severe(lastError);
            return false;
        }
    }

    /**
     * Flushes debounced YAML writes and captures one optimistic filesystem
     * generation. Concurrent mutations are never blocked; an affected capture
     * is discarded and retried.
     */
    public GenerationSnapshot captureSnapshot(Path stagingDirectory, int maxDepth,
                                              Predicate<Path> includeRelative, int attempts) throws IOException {
        if (!flush(flushTimeoutMillis)) throw new IOException("Could not flush YAML before snapshot");
        return captureGeneration(dataRoot, stagingDirectory, maxDepth, includeRelative, attempts);
    }

    public synchronized Health health() {
        return new Health(pending.size(), capacity, closed, completedWrites.get(), coalescedWrites.get(),
                failedWrites.get(), rejectedWrites.get(), lastWriteDurationMillis, lastError);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        flush();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(flushTimeoutMillis, TimeUnit.MILLISECONDS)) executor.shutdownNow();
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Path requireInsideRoot(Path target) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(dataRoot) || normalized.equals(dataRoot)) {
            throw new IOException("Refusing YAML write outside plugin data folder: " + normalized);
        }
        return normalized;
    }

    private void recordFailure(Exception error) {
        failedWrites.incrementAndGet();
        lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
        logger.severe("Atomic YAML save failed: " + lastError);
    }

    static void atomicWrite(Path target, byte[] contents, Path allowedRoot) throws IOException {
        trackedMutation(allowedRoot, () -> atomicWriteUntracked(target, contents, allowedRoot));
    }

    static void atomicBatchWrite(Map<Path, byte[]> files, Path allowedRoot) throws IOException {
        trackedMutation(allowedRoot, () -> {
            for (Map.Entry<Path, byte[]> entry : files.entrySet()) {
                atomicWriteUntracked(entry.getKey(), entry.getValue(), allowedRoot);
            }
        });
    }

    static void trackedMutation(Path root, IoAction action) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        GenerationTracker tracker = GENERATIONS.computeIfAbsent(normalizedRoot, ignored -> new GenerationTracker());
        // Seqlock protocol: an odd generation always means that at least one
        // writer may expose an intermediate multi-file state. State changes
        // are synchronized only for a few atomic operations; file I/O remains
        // fully concurrent and is never held behind the snapshot copier.
        synchronized (tracker) {
            if (tracker.activeWriters.incrementAndGet() == 1) tracker.generation.incrementAndGet();
        }
        try {
            action.run();
        } finally {
            synchronized (tracker) {
                if (tracker.activeWriters.decrementAndGet() == 0) {
                    tracker.generation.incrementAndGet();
                    tracker.notifyAll();
                }
            }
        }
    }

    static void trackedFileMutation(Path root, Path target, IoAction action) throws IOException {
        trackedMutation(root, () -> {
            synchronized (fileLock(target)) { action.run(); }
        });
    }

    private static void atomicWriteUntracked(Path target, byte[] contents, Path allowedRoot) throws IOException {
        Path root = allowedRoot.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) throw new IOException("Unsafe target path");
        synchronized (fileLock(normalized)) {
            Files.createDirectories(normalized.getParent());
            Path temporary = normalized.resolveSibling("." + normalized.getFileName() + "." + UUID.randomUUID() + ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(contents);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                try {
                    Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    static GenerationSnapshot captureGeneration(Path sourceRoot, Path stagingDirectory, int maxDepth,
                                                Predicate<Path> includeRelative, int attempts) throws IOException {
        Path root = sourceRoot.toAbsolutePath().normalize();
        Path staging = stagingDirectory.toAbsolutePath().normalize();
        if (staging.equals(root)) throw new IOException("Snapshot staging cannot equal source root");
        int safeAttempts = Math.clamp(attempts, 1, 20);
        int safeDepth = Math.clamp(maxDepth, 1, 16);
        long waitMillis = Math.clamp(safeAttempts * 250L, 1000L, 5000L);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        IOException last = null;
        for (int attempt = 1; attempt <= safeAttempts; attempt++) {
            last = null;
            // Waiting for an already-running writer must not consume a copy
            // attempt. Otherwise slow fsync can exhaust all retries before the
            // first filesystem walk even begins.
            GenerationStamp before = awaitStableGeneration(root, deadline);
            deleteSnapshotTree(staging);
            Files.createDirectories(staging);
            List<SnapshotFile> captured = new ArrayList<>();
            try (var walk = Files.walk(root, safeDepth)) {
                for (Path source : walk.filter(Files::isRegularFile).sorted().toList()) {
                    if (source.normalize().startsWith(staging)) continue;
                    Path relative = root.relativize(source);
                    if (!includeRelative.test(relative)) continue;
                    Path destination = staging.resolve(relative).normalize();
                    if (!destination.startsWith(staging)) throw new IOException("Unsafe snapshot path: " + relative);
                    Files.createDirectories(destination.getParent());
                    long modified = Files.getLastModifiedTime(source).toMillis();
                    captureFile(source, destination);
                    captured.add(new SnapshotFile(relative, destination, modified, Files.size(destination)));
                }
            } catch (IOException captureFailure) {
                last = captureFailure;
            }
            GenerationStamp after = generationStamp(root);
            if (last == null && before.generation() == after.generation() && after.activeWriters() == 0) {
                return new GenerationSnapshot(after.generation(), staging, captured);
            }
            if (before.generation() != after.generation() || after.activeWriters() != 0) {
                last = new IOException("Filesystem generation changed during snapshot (attempt " + attempt + ")");
            } else if (last != null) {
                deleteSnapshotTree(staging);
                throw last;
            }
            if (System.nanoTime() >= deadline) break;
            try { Thread.sleep(Math.min(5L * attempt, 50L)); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Snapshot interrupted", interrupted);
            }
        }
        deleteSnapshotTree(staging);
        throw last == null ? new IOException("Could not capture stable filesystem generation") : last;
    }

    private static GenerationStamp awaitStableGeneration(Path root, long deadlineNanos) throws IOException {
        GenerationTracker tracker = GENERATIONS.computeIfAbsent(root.toAbsolutePath().normalize(),
                ignored -> new GenerationTracker());
        synchronized (tracker) {
            while (tracker.activeWriters.get() != 0 || (tracker.generation.get() & 1L) != 0L) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0L) {
                    throw new IOException("Filesystem generation did not become stable before snapshot timeout");
                }
                try {
                    long millis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
                    tracker.wait(millis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Snapshot interrupted", interrupted);
                }
            }
            return new GenerationStamp(tracker.generation.get(), 0);
        }
    }

    private static void captureFile(Path source, Path destination) throws IOException {
        // Windows cannot atomically replace a path while Files.copy has it
        // open. Coordinate only this one file; unrelated YAML writers remain
        // completely unblocked and the global generation still detects races.
        synchronized (fileLock(source)) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static Object fileLock(Path file) {
        return FILE_LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new Object());
    }

    private static GenerationStamp generationStamp(Path root) {
        GenerationTracker tracker = GENERATIONS.computeIfAbsent(root.toAbsolutePath().normalize(),
                ignored -> new GenerationTracker());
        // A writer may finish between two field reads. Double-read the
        // generation so that such a torn observation is always considered busy.
        long before = tracker.generation.get();
        int active = tracker.activeWriters.get();
        long after = tracker.generation.get();
        boolean unstable = before != after || (after & 1L) != 0L;
        return new GenerationStamp(after, unstable ? Math.max(1, active) : active);
    }

    private static void deleteSnapshotTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
