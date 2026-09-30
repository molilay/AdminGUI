package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;

public class QuickActionListener implements Listener {

    private final AdvancedModeratorGUI plugin;
    public QuickActionListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/")) return;
        String[] parts = msg.split(" ");
        String cmd = parts[0].toLowerCase().replaceFirst("/", "");

        if ((cmd.equals("ban") || cmd.equals("tempban") || cmd.equals("mute") || cmd.equals("kick") || cmd.equals("warn")) && parts.length >= 3) {
            Player staff = event.getPlayer();
            if (staff.hasPermission("amgui.trial.mod")) {
                String type = cmd.equals("tempban") ? "ban" : cmd;
                String target = parts[1];
                String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                long duration = cmd.equals("tempban") ? parseDurationFromArgs(parts) : 0;
                if (cmd.equals("mute")) duration = 3600;
                event.setCancelled(true);
                plugin.getTrialModerationManager().requestAction(staff, type, target, reason, duration);
            }
        }
    }

    private long parseDurationFromArgs(String[] args) {
        for (String a : args) {
            long d = TimeUtils.parseDuration(a);
            if (d > 0) return d;
        }
        return 0;
    }
}
