package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit ownership for Bukkit tasks created by the plugin. */
public final class PluginScheduler implements AutoCloseable {
    public record Health(boolean running, int activeTasks) {}

    private final AdvancedModeratorGUI plugin;
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public PluginScheduler(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public void start() { running.set(true); }

    public void timer(String name, Runnable action, long delay, long period, boolean async) {
        requireRunning(name);
        Runnable guarded = () -> {
            if (!running.get()) return;
            try { action.run(); }
            catch (RuntimeException error) { plugin.getLogger().warning("Scheduled task '" + name + "' failed: " + error.getMessage()); }
        };
        BukkitTask task = async
                ? plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, guarded, delay, period)
                : plugin.getServer().getScheduler().runTaskTimer(plugin, guarded, delay, period);
        replace(name, task);
    }

    public void later(String name, Runnable action, long delay, boolean async) {
        requireRunning(name);
        Runnable guarded = () -> {
            try { if (running.get()) action.run(); }
            finally { tasks.remove(name); }
        };
        BukkitTask task = async
                ? plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, guarded, delay)
                : plugin.getServer().getScheduler().runTaskLater(plugin, guarded, delay);
        replace(name, task);
    }

    private void replace(String name, BukkitTask task) {
        BukkitTask previous = tasks.put(name, task);
        if (previous != null) previous.cancel();
    }

    private void requireRunning(String name) {
        if (!running.get()) throw new IllegalStateException("Scheduler is stopped: " + name);
    }

    public Health health() { return new Health(running.get(), tasks.size()); }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        tasks.values().forEach(BukkitTask::cancel);
        tasks.clear();
    }
}
