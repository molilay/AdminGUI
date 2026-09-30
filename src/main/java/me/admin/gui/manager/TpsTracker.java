package me.admin.gui.manager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedList;

public class TpsTracker {

    private final LinkedList<Double> history = new LinkedList<>();
    private static final int MAX_SAMPLES = 60;
    private final JavaPlugin plugin;
    private volatile org.bukkit.scheduler.BukkitTask task;

    public TpsTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            double tps = Bukkit.getTPS()[0];
            synchronized (history) {
                history.addLast(tps);
                if (history.size() > MAX_SAMPLES) {
                    history.removeFirst();
                }
            }
        }, 40L, 40L);
    }

    public synchronized void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public boolean isRunning() { return task != null; }

    public double getCurrent() {
        return Bukkit.getTPS()[0];
    }

    public double getAverage() {
        synchronized (history) {
            if (history.isEmpty()) return getCurrent();
            return history.stream().mapToDouble(d -> d).average().orElse(getCurrent());
        }
    }

    public double getMin() {
        synchronized (history) {
            if (history.isEmpty()) return getCurrent();
            return history.stream().mapToDouble(d -> d).min().orElse(getCurrent());
        }
    }

    public int getSampleCount() {
        synchronized (history) {
            return history.size();
        }
    }

    public String getColor(double tps) {
        if (tps > 18.0) return "&a";
        if (tps > 15.0) return "&e";
        return "&c";
    }
}
