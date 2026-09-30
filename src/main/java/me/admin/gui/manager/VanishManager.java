package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager implements Listener {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private static final String SEE_PERMISSION = "amgui.vanish.see";

    public VanishManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player player) {
        if (vanished.contains(player.getUniqueId())) {
            unvanish(player);
            return false;
        } else {
            vanish(player);
            return true;
        }
    }

    public void vanish(Player player) {
        vanished.add(player.getUniqueId());
        player.setInvisible(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player) && !p.hasPermission(SEE_PERMISSION)) {
                p.hidePlayer(plugin, player);
            }
        }

        player.sendMessage("§7Вы вошли в режим невидимки.");
        plugin.getLogger().info(player.getName() + " vanished");
        plugin.getAuditManager().record(player, "vanish.enable", player.getName(), player.getUniqueId(), "");
    }

    public void unvanish(Player player) {
        vanished.remove(player.getUniqueId());
        player.setInvisible(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showPlayer(plugin, player);
        }

        player.sendMessage("§7Вы вышли из режима невидимки.");
        plugin.getLogger().info(player.getName() + " unvanished");
        plugin.getAuditManager().record(player, "vanish.disable", player.getName(), player.getUniqueId(), "");
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public int getVanishedCount() {
        return vanished.size();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        boolean staffSee = joined.hasPermission(SEE_PERMISSION);

        for (UUID vid : vanished) {
            Player vp = Bukkit.getPlayer(vid);
            if (vp != null && vp.isOnline() && !vp.equals(joined)) {
                if (!staffSee) {
                    joined.hidePlayer(plugin, vp);
                }
            }
        }

        if (vanished.contains(joined.getUniqueId())) {
            vanish(joined);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (vanished.contains(player.getUniqueId())) {
            player.setInvisible(false);
            player.setCollidable(true);
            player.setCanPickupItems(true);
        }
    }

    public void applyToAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (vanished.contains(player.getUniqueId())) {
                player.setInvisible(true);
                player.setCollidable(false);
                player.setCanPickupItems(false);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player) && !p.hasPermission(SEE_PERMISSION)) {
                        p.hidePlayer(plugin, player);
                    }
                }
            }
        }
    }

    public void showVanishedTo(Player player) {
        if (!player.hasPermission(SEE_PERMISSION)) {
            for (UUID vid : vanished) {
                Player vp = Bukkit.getPlayer(vid);
                if (vp != null && vp.isOnline()) {
                    player.hidePlayer(plugin, vp);
                }
            }
        }
    }
}
