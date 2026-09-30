package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerInventoryCache {

    private record CacheRef(File file, long generation) { }

    private final AdvancedModeratorGUI plugin;
    private final File cacheDir;
    private final AtomicLong generations = new AtomicLong(System.currentTimeMillis());

    public PlayerInventoryCache(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "inventory-cache");
        cacheDir.mkdirs();
    }

    public synchronized void cache(Player player) {
        File file = new File(cacheDir, player.getUniqueId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("contents", Arrays.asList(player.getInventory().getContents()));
        config.set("ender", Arrays.asList(player.getEnderChest().getContents()));
        config.set("timestamp", System.currentTimeMillis());
        config.set("generation", generations.incrementAndGet());
        // Synchronous atomic replace ensures a retention delete cannot be followed
        // by an older queued write that resurrects stale inventory data.
        YamlPersistenceService.saveYamlNow(plugin, file, config, "player inventory cache");
        pruneInternal(System.currentTimeMillis());
    }

    public synchronized ItemStack[] getInventory(UUID uuid) {
        YamlConfiguration config = loadFresh(uuid);
        if (config == null) return null;
        List<ItemStack> list = readItems(config.getList("contents"));
        return list == null ? null : list.toArray(new ItemStack[0]);
    }

    public synchronized ItemStack[] getEnderChest(UUID uuid) {
        YamlConfiguration config = loadFresh(uuid);
        if (config == null) return null;
        List<ItemStack> list = readItems(config.getList("ender"));
        return list == null ? null : list.toArray(new ItemStack[0]);
    }

    public synchronized void clear(UUID uuid) {
        File file = new File(cacheDir, uuid + ".yml");
        if (!file.exists()) return;
        YamlConfiguration selected = YamlConfiguration.loadConfiguration(file);
        deleteIfGeneration(file, selected.getLong("generation", selected.getLong("timestamp", -1L)));
    }

    public synchronized int prune() {
        return pruneInternal(System.currentTimeMillis());
    }

    private YamlConfiguration loadFresh(UUID uuid) {
        File file = new File(cacheDir, uuid + ".yml");
        if (!file.exists()) return null;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        long timestamp = config.getLong("timestamp", -1L);
        long ttlDays = cacheTtlDays();
        if (timestamp <= 0 || timestamp < System.currentTimeMillis() - ttlDays * 86_400_000L) {
            deleteIfGeneration(file, config.getLong("generation", timestamp));
            return null;
        }
        return config;
    }

    private int pruneInternal(long now) {
        File[] files = cacheDir.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (files == null || files.length == 0) return 0;
        List<RetentionPolicy.Entry> entries = new ArrayList<>();
        Map<String, CacheRef> refs = new HashMap<>();
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            long timestamp = yaml.getLong("timestamp", -1L);
            long generation = yaml.getLong("generation", timestamp);
            String id = file.getName();
            String owner = id.substring(0, id.length() - 4);
            entries.add(new RetentionPolicy.Entry(owner, id, timestamp, generation));
            refs.put(id, new CacheRef(file, generation));
        }
        int maxPerPlayer = Math.clamp(plugin.getConfig().getInt(
                "inventory-retention.cache.max-per-player", 1), 1, 5);
        int maxTotal = Math.clamp(plugin.getConfig().getInt(
                "inventory-retention.cache.max-total", 1000), 1, 100_000);
        Set<String> deletes = RetentionPolicy.selectDeletes(entries, now,
                cacheTtlDays() * 86_400_000L, maxPerPlayer, maxTotal);
        int deleted = 0;
        for (String id : deletes) {
            CacheRef ref = refs.get(id);
            if (ref != null && deleteIfGeneration(ref.file(), ref.generation())) deleted++;
        }
        return deleted;
    }

    private long cacheTtlDays() {
        return Math.clamp(plugin.getConfig().getLong("inventory-retention.cache.ttl-days", 7L), 1L, 3650L);
    }

    private boolean deleteIfGeneration(File file, long expectedGeneration) {
        if (!file.exists()) return false;
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        long actual = current.getLong("generation", current.getLong("timestamp", Long.MIN_VALUE));
        if (actual != expectedGeneration) return false;
        try { return Files.deleteIfExists(file.toPath()); }
        catch (IOException error) {
            plugin.getLogger().warning("Cannot prune inventory cache: " + error.getMessage());
            return false;
        }
    }

    private static List<ItemStack> readItems(List<?> values) {
        if (values == null) return null;
        List<ItemStack> result = new ArrayList<>(values.size());
        for (Object value : values) result.add(value instanceof ItemStack item ? item : null);
        return result;
    }
}
