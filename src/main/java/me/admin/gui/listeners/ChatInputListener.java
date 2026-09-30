package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatInputListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public ChatInputListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getChatInputManager().hasInput(player)) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getChatInputManager().handleInput(player, message));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChatInputManager().cancel(event.getPlayer().getUniqueId());
        plugin.getGuiManager().cleanup(event.getPlayer().getUniqueId());
        me.admin.gui.gui.CasesGUI.clearState(event.getPlayer().getUniqueId());
    }
}
