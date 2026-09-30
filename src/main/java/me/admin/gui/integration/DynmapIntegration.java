package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DynmapIntegration {

    private final AdvancedModeratorGUI plugin;
    private boolean enabled = false;
    private Object dynmapAPI;
    private Method setPlayerVis;
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();

    public DynmapIntegration(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        try {
            if (Bukkit.getPluginManager().getPlugin("dynmap") != null) {
                dynmapAPI = Bukkit.getPluginManager().getPlugin("dynmap");
                setPlayerVis = dynmapAPI.getClass().getMethod("setPlayerVis", String.class, boolean.class);
                enabled = true;
                plugin.getLogger().info("Dynmap подключён.");
            } else {
                enabled = false;
            }
        } catch (Exception e) {
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    public void hidePlayer(Player player) {
        if (!enabled) return;
        if (hiddenPlayers.add(player.getUniqueId())) {
            try {
                setPlayerVis.invoke(dynmapAPI, player.getName(), false);
            } catch (Exception ignored) {}
        }
    }

    public void showPlayer(Player player) {
        if (!enabled) return;
        if (hiddenPlayers.remove(player.getUniqueId())) {
            try {
                setPlayerVis.invoke(dynmapAPI, player.getName(), true);
            } catch (Exception ignored) {}
        }
    }

    public void onVanishToggle(Player player, boolean vanished) {
        if (vanished) hidePlayer(player);
        else showPlayer(player);
    }
}
