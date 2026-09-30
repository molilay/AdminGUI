package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.scheduler.BukkitTask;

/** Lightweight scheduler that submits maintenance work to the bounded runtime. */
public final class RuntimeMaintenance implements AutoCloseable {
    private final AdvancedModeratorGUI plugin;
    private volatile BukkitTask privacyPruneTask;

    public RuntimeMaintenance(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public synchronized void start() {
        if (privacyPruneTask != null) return;
        long period = Math.clamp(plugin.getConfig().getLong("privacy.ip-retention-prune-ticks", 432_000L),
                1200L, 1_728_000L);
        privacyPruneTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                plugin.getTaskExecutor().supply("ip-retention-prune", () -> {
                    plugin.getAltDetector().purgeExpiredRawIps();
                    return null;
                }), period, period);
    }

    @Override
    public synchronized void close() {
        if (privacyPruneTask != null) privacyPruneTask.cancel();
        privacyPruneTask = null;
    }
}
