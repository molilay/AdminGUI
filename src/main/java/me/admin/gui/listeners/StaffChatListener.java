package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class StaffChatListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public StaffChatListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getStaffChatManager().isToggled(player)) {
            event.setCancelled(true);
            plugin.getStaffChatManager().sendStaffMessage(player.getName(), event.getMessage());
        }
    }
}
