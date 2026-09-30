package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class WorldGuardIntegration {

    private final AdvancedModeratorGUI plugin;
    private boolean enabled = false;
    private Object wgPlugin;
    private Method getRegionManager;
    private Method getRegion;
    private Method getFlags;
    private final Set<String> bannedPlayers = new HashSet<>();

    public WorldGuardIntegration(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wgClass.getMethod("getInstance").invoke(null);
            wgPlugin = instance;
            enabled = true;
            plugin.getLogger().info("WorldGuard подключён.");
        } catch (Exception e) {
            try {
                Class<?> pluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (wgPlugin == null) throw new Exception();
                enabled = true;
                plugin.getLogger().info("WorldGuard подключён (legacy).");
            } catch (Exception ex) {
                enabled = false;
            }
        }
    }

    public boolean isEnabled() { return enabled; }

    public boolean isRegionBanned(Player player, String regionId) {
        if (!enabled) return false;
        return bannedPlayers.contains(player.getName() + ":" + regionId);
    }

    public void banRegion(Player player, String regionId) {
        bannedPlayers.add(player.getName() + ":" + regionId);
        Location loc = player.getLocation();
        if (isInRegion(loc, regionId)) {
            player.teleport(player.getWorld().getSpawnLocation());
            player.sendMessage("§cВам запрещён вход в регион §f" + regionId);
        }
    }

    public void unbanRegion(Player player, String regionId) {
        bannedPlayers.remove(player.getName() + ":" + regionId);
    }

    public boolean isInRegion(Location loc, String regionId) {
        try {
            Object world = Bukkit.getServer().getClass().getMethod("getWorld", String.class).invoke(Bukkit.getServer(), loc.getWorld().getName());

            Object regionContainer;
            if (wgPlugin.getClass().getName().contains("WorldGuard")) {
                Method getPlatform = wgPlugin.getClass().getMethod("getPlatform");
                Object platform = getPlatform.invoke(wgPlugin);
                Method getRC = platform.getClass().getMethod("getRegionContainer");
                regionContainer = getRC.invoke(platform);
            } else {
                Method getRM = wgPlugin.getClass().getMethod("getRegionManager", Bukkit.getWorlds().get(0).getClass());
                regionContainer = getRM.invoke(wgPlugin, world);
            }

            if (regionContainer == null) return false;

            Method get = regionContainer.getClass().getMethod("get", String.class);
            Object region = get.invoke(regionContainer, regionId);
            if (region == null) return false;

            Class<?> regionClass = region.getClass();
            try {
                Method contains = regionClass.getMethod("contains", Location.class);
                return (boolean) contains.invoke(region, loc);
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
