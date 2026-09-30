package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;

import java.util.List;

public class AutoBroadcasterManager {

    private final AdvancedModeratorGUI plugin;
    private int taskId = -1;
    private int index = 0;

    public AutoBroadcasterManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (taskId != -1) return;
        if (!plugin.getConfig().getBoolean("auto-broadcaster.enabled", false)) return;
        long interval = plugin.getConfig().getLong("auto-broadcaster.interval", 600) * 20L;
        List<String> messages = plugin.getConfig().getStringList("auto-broadcaster.messages");
        if (messages.isEmpty()) return;

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String msg = messages.get(index);
            Bukkit.getServer().broadcast(me.admin.gui.utils.TextUtil.legacy(msg.replace("&", "§")));
            index = (index + 1) % messages.size();
        }, interval, interval).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void reload() {
        stop();
        start();
    }
}
