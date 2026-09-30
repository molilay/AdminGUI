package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Plugin-owned bounded worker pool with lifecycle and callback gating. */
public final class PluginTaskExecutor implements AutoCloseable {

    public record Health(boolean running, int active, int queued, int capacity,
                         long submitted, long completed, long rejected, int pendingFutures) {}

    private final AdvancedModeratorGUI plugin;
    private final Set<CompletableFuture<?>> futures = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private volatile ThreadPoolExecutor executor;

    public PluginTaskExecutor(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) return;
        int threads = Math.clamp(plugin.getConfig().getInt("runtime.executor.threads", 2), 1, 8);
        int capacity = Math.clamp(plugin.getConfig().getInt("runtime.executor.queue-capacity", 256), 16, 4096);
        AtomicLong sequence = new AtomicLong();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "amgui-worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) ->
                    plugin.getLogger().severe("Runtime worker crashed: " + error.getMessage()));
            return thread;
        };
        executor = new ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
        accepting.set(true);
    }

    public <T> CompletableFuture<T> supply(String label, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        ThreadPoolExecutor current = executor;
        if (!accepting.get() || current == null) {
            rejected.incrementAndGet();
            return CompletableFuture.failedFuture(new RejectedExecutionException("runtime is stopped: " + label));
        }
        futures.add(result);
        submitted.incrementAndGet();
        try {
            current.execute(() -> {
                try {
                    if (!accepting.get()) throw new RejectedExecutionException("runtime stopped before: " + label);
                    result.complete(action.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    completed.incrementAndGet();
                    futures.remove(result);
                }
            });
        } catch (RejectedExecutionException full) {
            rejected.incrementAndGet();
            futures.remove(result);
            result.completeExceptionally(full);
        }
        return result;
    }

    /** The callback is discarded once shutdown starts, including racey future completions. */
    public boolean runOnMain(Runnable callback) {
        if (!accepting.get() || !plugin.isEnabled()) return false;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (accepting.get() && plugin.isEnabled()) callback.run();
            });
            return true;
        } catch (RuntimeException disabled) {
            return false;
        }
    }

    public Health health() {
        ThreadPoolExecutor current = executor;
        int queued = current == null ? 0 : current.getQueue().size();
        int remaining = current == null ? 0 : current.getQueue().remainingCapacity();
        return new Health(accepting.get(), current == null ? 0 : current.getActiveCount(), queued,
                queued + remaining, submitted.get(), completed.get(), rejected.get(), futures.size());
    }

    @Override
    public synchronized void close() {
        if (!accepting.compareAndSet(true, false)) return;
        RejectedExecutionException stopped = new RejectedExecutionException("plugin runtime stopped");
        futures.forEach(future -> future.completeExceptionally(stopped));
        futures.clear();
        ThreadPoolExecutor current = executor;
        if (current == null) return;
        current.shutdownNow();
        try {
            if (!current.awaitTermination(2L, TimeUnit.SECONDS))
                plugin.getLogger().warning("Runtime worker pool did not terminate within 2 seconds.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
