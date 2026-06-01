package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffChatManager {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> staffChatToggled = ConcurrentHashMap.newKeySet();

    public StaffChatManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public void toggle(Player player) {
        if (staffChatToggled.contains(player.getUniqueId())) {
            staffChatToggled.remove(player.getUniqueId());
            player.sendMessage("§cStaffChat выключен.");
        } else {
            staffChatToggled.add(player.getUniqueId());
            player.sendMessage("§aStaffChat включён.");
        }
    }

    public boolean isToggled(Player player) {
        return staffChatToggled.contains(player.getUniqueId());
    }

    public void sendStaffMessage(String name, String message) {
        String formatted = "§8[§bSC§8] §7" + name + "§f: " + message;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.staffchat")) {
                staff.sendMessage(formatted);
            }
        }
        plugin.getLogger().info("[SC] " + name + ": " + message);
    }

    public void notifyJoin(Player player) {
        String msg = plugin.getConfigManager().getFormattedMessage("staff-join", "player", player.getName());
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.staffchat") && !staff.equals(player)) {
                staff.sendMessage(msg);
            }
        }
    }

    public void toggleCleanup(Player player) {
        staffChatToggled.remove(player.getUniqueId());
    }

    public void notifyLeave(Player player) {
        String msg = plugin.getConfigManager().getFormattedMessage("staff-leave", "player", player.getName());
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.staffchat") && !staff.equals(player)) {
                staff.sendMessage(msg);
            }
        }
    }
}
