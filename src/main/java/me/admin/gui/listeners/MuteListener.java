package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class MuteListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public MuteListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMuteManager().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§cВы замьючены и не можете писать в чат.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getMuteManager().isMuted(player.getUniqueId())) return;

        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/msg ") || cmd.startsWith("/tell ") || cmd.startsWith("/w ")
                || cmd.equals("/msg") || cmd.equals("/tell") || cmd.equals("/w")) {
            event.setCancelled(true);
            player.sendMessage("§cВы замьючены и не можете писать в личные сообщения.");
        }
    }
}
