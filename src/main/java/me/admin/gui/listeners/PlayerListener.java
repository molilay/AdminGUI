package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public PlayerListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getFreezeManager().checkFrozenOnJoin(player);

        String ip = player.getAddress() != null ?
                player.getAddress().getAddress().getHostAddress() : "unknown";
        plugin.getAltDetector().recordLogin(player.getUniqueId(), player.getName(), ip);

        if (player.hasPermission("amgui.staffchat")) {
            plugin.getStaffChatManager().notifyJoin(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getGuiManager().unregister(player.getUniqueId());

        plugin.getPlayerInventoryCache().cache(player);

        if (player.hasPermission("amgui.staffchat")) {
            plugin.getStaffChatManager().notifyLeave(player);
        }
        plugin.getStaffChatManager().toggleCleanup(player);
    }
}
