package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.FreezeManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezeListener implements Listener {

    private final AdvancedModeratorGUI plugin;
    private final Map<UUID, Location> frozenLocations = new HashMap<>();

    public FreezeListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getFreezeManager().isFrozen(event.getPlayer())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            event.setCancelled(true);
            event.getPlayer().teleport(frozenLocations.computeIfAbsent(
                    event.getPlayer().getUniqueId(), k -> from));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getConfigManager().isFreezeInteract() && plugin.getFreezeManager().isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFreezeManager().isFrozen(player)) return;
        if (!plugin.getConfigManager().isFreezeChat()) return;

        event.setCancelled(true);
        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission("amgui.freeze")) {
                staff.sendMessage("§c[Frozen] §7" + player.getName() + "§f: " + event.getMessage());
            }
        }
        player.sendMessage("§7[§cFrozen§7] §fСообщение отправлено модераторам: §7" + event.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isFreezeCommands()) return;
        if (!plugin.getFreezeManager().isFrozen(event.getPlayer())) return;

        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/msg ") || cmd.startsWith("/tell ") || cmd.startsWith("/w ")) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cВы не можете использовать команды в заморозке.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            plugin.getFreezeManager().unfreeze(player);
            frozenLocations.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        frozenLocations.remove(event.getPlayer().getUniqueId());
    }
}
