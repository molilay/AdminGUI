package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerPunishListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public PlayerPunishListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getInventoryRollbackManager().getSnapshots(event.getPlayer().getUniqueId()).forEach(snap -> {
            if (snap.reason() != null) {
                event.getPlayer().sendMessage("§eУ вас есть сохранение инвентаря от " +
                        new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(snap.timestamp())));
            }
        });
    }
}
