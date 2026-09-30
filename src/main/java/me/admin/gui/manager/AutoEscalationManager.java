package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoEscalationManager {

    private final AdvancedModeratorGUI plugin;
    private final Map<UUID, List<Long>> warnsTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Long> punishCooldowns = new ConcurrentHashMap<>();
    private volatile org.bukkit.scheduler.BukkitTask cleanupTask;

    public AutoEscalationManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (cleanupTask == null)
            cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 6000L, 6000L);
    }

    public synchronized void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cleanupTask = null;
    }

    public boolean isRunning() { return cleanupTask != null; }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("auto-escalation.enabled", false);
    }

    public void recordWarn(Player player) {
        if (!isEnabled()) return;
        UUID uid = player.getUniqueId();
        warnsTimestamps.computeIfAbsent(uid, k -> Collections.synchronizedList(new ArrayList<>())).add(System.currentTimeMillis());

        int maxPerHour = plugin.getConfig().getInt("auto-escalation.max-warns-per-hour", 3);
        long oneHour = 3600000L;
        long now = System.currentTimeMillis();

        List<Long> recent = warnsTimestamps.get(uid);
        recent.removeIf(t -> (now - t) > oneHour);

        if (recent.size() >= maxPerHour) {
            if (punishCooldowns.containsKey(uid) && (now - punishCooldowns.get(uid)) < 600000L) return;
            punishCooldowns.put(uid, now);
            recent.clear();

            String action = plugin.getConfig().getString("auto-escalation.action", "mute");
            long duration = plugin.getConfig().getLong("auto-escalation.duration", 3600);
            String reason = plugin.getConfig().getString("auto-escalation.reason", "Авто-эскалация: превышение лимита варнов");

            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (action) {
                    case "mute" -> plugin.getMuteManager().mute(player, reason, "AutoEscalation", duration);
                    case "ban" -> {
                        String name = player.getName();
                        me.admin.gui.utils.BanService.banProfile(name, reason,
                                duration > 0 ? java.time.Instant.ofEpochMilli(now + duration * 1000) : null,
                                "AutoEscalation");
                        player.kick(me.admin.gui.utils.TextUtil.legacy("§cАвто-наказание.\n§7Причина: " + reason));
                    }
                    case "kick" -> player.kick(me.admin.gui.utils.TextUtil.legacy("§cАвто-кик.\n§7Причина: " + reason));
                }
                plugin.getDatabaseManager().logPunishment("auto-" + action, "AutoEscalation", player.getName(), reason, duration);
                plugin.getStaffActivityManager().ifPresent(s -> s.log("AutoEscalation", action, player.getName(), reason, duration));
            });
        }
    }

    private void cleanup() {
        long oneHour = 3600000L;
        long now = System.currentTimeMillis();
        warnsTimestamps.values().forEach(list -> list.removeIf(t -> (now - t) > oneHour));
        punishCooldowns.entrySet().removeIf(e -> (now - e.getValue()) > 600000L);
    }
}
