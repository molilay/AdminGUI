package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatInputListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public ChatInputListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getChatInputManager().hasInput(player)) {
            event.setCancelled(true);
            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getChatInputManager().handleInput(player, message));
        }
    }
}
