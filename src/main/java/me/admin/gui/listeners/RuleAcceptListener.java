package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class RuleAcceptListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public RuleAcceptListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getRuleAcceptManager().isEnabled()) return;
        if (plugin.getRuleAcceptManager().hasAccepted(event.getPlayer())) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getRuleAcceptManager().openAcceptGUI(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // cleanup handled by save on accept
    }
}
