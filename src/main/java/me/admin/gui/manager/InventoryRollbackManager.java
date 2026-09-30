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

public class InventoryRollbackManager {

    private record SnapshotRef(File file, String key, long generation) { }

    private final AdvancedModeratorGUI plugin;
    private final File snapsDir;
    private final AtomicLong generations = new AtomicLong(System.currentTimeMillis());

    public InventoryRollbackManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.snapsDir = new File(plugin.getDataFolder(), "inventory-snapshots");
        snapsDir.mkdirs();
    }

    public synchronized void saveSnapshot(Player player, String reason) {
        File file = new File(snapsDir, player.getUniqueId() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        long generation = generations.incrementAndGet();
        String key = now + "-" + UUID.randomUUID().toString().substring(0, 8);

        config.set(key + ".items", new ArrayList<>(Arrays.asList(player.getInventory().getContents())));
        config.set(key + ".armor", new ArrayList<>(Arrays.asList(player.getInventory().getArmorContents())));
        config.set(key + ".extra", new ArrayList<>(Arrays.asList(player.getInventory().getExtraContents())));
        config.set(key + ".date", now);
        config.set(key + ".generation", generation);
        config.set(key + ".reason", reason == null ? "unknown" : reason);
        YamlPersistenceService.saveYamlNow(plugin, file, config, "inventory snapshot");
        pruneInternal(now);
    }

    public synchronized void restoreSnapshot(Player player, long timestamp) {
        File file = new File(snapsDir, player.getUniqueId() + ".yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            if (config.getLong(key + ".date", -1L) != timestamp) continue;
            List<ItemStack> items = readItems(config.getList(key + ".items"));
            List<ItemStack> armor = readItems(config.getList(key + ".armor"));
            List<ItemStack> extra = readItems(config.getList(key + ".extra"));
            if (items != null) player.getInventory().setContents(items.toArray(new ItemStack[0]));
            if (armor != null) player.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));
            if (extra != null) player.getInventory().setExtraContents(extra.toArray(new ItemStack[0]));
            break;
        }
    }

    public synchronized List<SnapshotInfo> getSnapshots(UUID uuid) {
        pruneInternal(System.currentTimeMillis());
        File file = new File(snapsDir, uuid + ".yml");
        if (!file.exists()) return List.of();
        List<SnapshotInfo> snapshots = new ArrayList<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            snapshots.add(new SnapshotInfo(config.getLong(key + ".date"),
                    config.getString(key + ".reason", "unknown")));
        }
        snapshots.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return snapshots;
    }

    public synchronized int prune() {
        return pruneInternal(System.currentTimeMillis());
    }

    private int pruneInternal(long now) {
        File[] files = snapsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (files == null || files.length == 0) return 0;
        List<RetentionPolicy.Entry> entries = new ArrayList<>();
        Map<String, SnapshotRef> refs = new HashMap<>();
        for (File file : files) {
            String owner = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                long timestamp = yaml.getLong(key + ".date", -1L);
                long generation = yaml.getLong(key + ".generation", timestamp);
                String id = file.getName() + "#" + key;
                entries.add(new RetentionPolicy.Entry(owner, id, timestamp, generation));
                refs.put(id, new SnapshotRef(file, key, generation));
            }
        }
        long ttlDays = Math.clamp(plugin.getConfig().getLong(
                "inventory-retention.snapshots.ttl-days", 30L), 1L, 3650L);
        int maxPerPlayer = Math.clamp(plugin.getConfig().getInt(
                "inventory-retention.snapshots.max-per-player", 20), 1, 500);
        int maxTotal = Math.clamp(plugin.getConfig().getInt(
                "inventory-retention.snapshots.max-total", 5000), maxPerPlayer, 100_000);
        Set<String> deletes = RetentionPolicy.selectDeletes(entries, now,
                ttlDays * 86_400_000L, maxPerPlayer, maxTotal);
        if (deletes.isEmpty()) return 0;

        Map<File, List<SnapshotRef>> byFile = new HashMap<>();
        for (String id : deletes) {
            SnapshotRef ref = refs.get(id);
            if (ref != null) byFile.computeIfAbsent(ref.file(), ignored -> new ArrayList<>()).add(ref);
        }
        int deleted = 0;
        for (Map.Entry<File, List<SnapshotRef>> row : byFile.entrySet()) {
            File file = row.getKey();
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            for (SnapshotRef ref : row.getValue()) {
                long actualGeneration = current.getLong(ref.key() + ".generation",
                        current.getLong(ref.key() + ".date", Long.MIN_VALUE));
                if (actualGeneration != ref.generation()) continue; // replaced after selection
                current.set(ref.key(), null);
                deleted++;
            }
            if (current.getKeys(false).isEmpty()) {
                try { Files.deleteIfExists(file.toPath()); }
                catch (IOException error) { plugin.getLogger().warning("Cannot prune snapshot file: " + error.getMessage()); }
            } else {
                YamlPersistenceService.saveYamlNow(plugin, file, current, "inventory snapshot retention");
            }
        }
        return deleted;
    }

    public record SnapshotInfo(long timestamp, String reason) { }

    private static List<ItemStack> readItems(List<?> values) {
        if (values == null) return null;
        List<ItemStack> result = new ArrayList<>(values.size());
        for (Object value : values) result.add(value instanceof ItemStack item ? item : null);
        return result;
    }
}
