package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class StaffChatListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public StaffChatListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getStaffChatManager().isToggled(player)) {
            event.setCancelled(true);
            String playerName = player.getName();
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.getStaffChatManager().sendStaffMessage(playerName, message));
        }
    }
}
