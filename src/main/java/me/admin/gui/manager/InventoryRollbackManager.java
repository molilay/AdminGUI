package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class InventoryRollbackManager {

    private final AdvancedModeratorGUI plugin;
    private final File snapsDir;

    public InventoryRollbackManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.snapsDir = new File(plugin.getDataFolder(), "inventory-snapshots");
        snapsDir.mkdirs();
    }

    public void saveSnapshot(Player player, String reason) {
        File file = new File(snapsDir, player.getUniqueId() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String key = System.currentTimeMillis() + "-" + reason.replaceAll("[^a-zA-Zа-яА-Я0-9_]", "_");
        List<ItemStack> items = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
        List<ItemStack> armor = new ArrayList<>(Arrays.asList(player.getInventory().getArmorContents()));
        List<ItemStack> extra = new ArrayList<>(Arrays.asList(player.getInventory().getExtraContents()));

        config.set(key + ".items", items);
        config.set(key + ".armor", armor);
        config.set(key + ".extra", extra);
        config.set(key + ".date", System.currentTimeMillis());
        config.set(key + ".reason", reason);

        try {
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save inventory snapshot: " + e.getMessage());
        }
    }

    public void restoreSnapshot(Player player, long timestamp) {
        File file = new File(snapsDir, player.getUniqueId() + ".yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            if (!key.startsWith(String.valueOf(timestamp))) continue;

            List<ItemStack> items = (List<ItemStack>) config.getList(key + ".items");
            List<ItemStack> armor = (List<ItemStack>) config.getList(key + ".armor");
            List<ItemStack> extra = (List<ItemStack>) config.getList(key + ".extra");

            if (items != null) player.getInventory().setContents(items.toArray(new ItemStack[0]));
            if (armor != null) player.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));
            if (extra != null) player.getInventory().setExtraContents(extra.toArray(new ItemStack[0]));
            break;
        }
    }

    public List<SnapshotInfo> getSnapshots(UUID uuid) {
        File file = new File(snapsDir, uuid + ".yml");
        if (!file.exists()) return List.of();

        List<SnapshotInfo> snapshots = new ArrayList<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            long timestamp = config.getLong(key + ".date");
            String reason = config.getString(key + ".reason", "unknown");
            snapshots.add(new SnapshotInfo(timestamp, reason));
        }
        snapshots.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return snapshots;
    }

    public record SnapshotInfo(long timestamp, String reason) {}
}
