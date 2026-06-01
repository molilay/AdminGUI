package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PlayerInventoryCache {

    private final AdvancedModeratorGUI plugin;
    private final File cacheDir;

    public PlayerInventoryCache(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "inventory-cache");
        cacheDir.mkdirs();
    }

    public void cache(Player player) {
        File file = new File(cacheDir, player.getUniqueId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("contents", Arrays.asList(player.getInventory().getContents()));
        config.set("ender", Arrays.asList(player.getEnderChest().getContents()));
        config.set("timestamp", System.currentTimeMillis());
        try { config.save(file); }
        catch (Exception ignored) {}
    }

    public ItemStack[] getInventory(UUID uuid) {
        File file = new File(cacheDir, uuid + ".yml");
        if (!file.exists()) return null;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> list = (List<ItemStack>) config.getList("contents");
        if (list == null) return null;
        return list.toArray(new ItemStack[0]);
    }

    public ItemStack[] getEnderChest(UUID uuid) {
        File file = new File(cacheDir, uuid + ".yml");
        if (!file.exists()) return null;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> list = (List<ItemStack>) config.getList("ender");
        if (list == null) return null;
        return list.toArray(new ItemStack[0]);
    }

    public void clear(UUID uuid) {
        File file = new File(cacheDir, uuid + ".yml");
        if (file.exists()) file.delete();
    }
}
