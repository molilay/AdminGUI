package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AltDetector {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;

    public AltDetector(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("Failed to create playerdata.yml: " + e.getMessage()); }
        }
    }

    public List<String> findAlts(String ip, UUID excludeId) {
        List<String> alts = new ArrayList<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String storedIp = config.getString(key + ".last-ip", "");
                String name = config.getString(key + ".name", "?");
                if (storedIp.equals(ip) && !uuid.equals(excludeId)) alts.add(name);
            } catch (IllegalArgumentException ignored) {}
        }
        return alts;
    }

    public List<String> findAlts(UUID playerId) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        String ip = config.getString(playerId + ".last-ip", "");
        if (ip.isEmpty()) return List.of();
        return findAlts(ip, playerId);
    }

    public Map<String, UUID> findAltUuids(UUID playerId) {
        Map<String, UUID> result = new LinkedHashMap<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        String ip = config.getString(playerId + ".last-ip", "");
        if (ip.isEmpty()) return result;
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String storedIp = config.getString(key + ".last-ip", "");
                String name = config.getString(key + ".name", "?");
                if (storedIp.equals(ip) && !uuid.equals(playerId)) result.put(name, uuid);
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public void recordLogin(UUID uuid, String name, String ip) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        long now = System.currentTimeMillis();

        config.set(uuid + ".name", name);
        config.set(uuid + ".last-ip", ip);
        config.set(uuid + ".last-seen", now);

        List<Map<String, Object>> history = (List<Map<String, Object>>) config.getList(uuid + ".ip-history");
        if (history == null) history = new ArrayList<>();
        boolean found = false;
        for (Map<String, Object> entry : history) {
            if (ip.equals(entry.get("ip"))) {
                entry.put("last", now);
                found = true;
                break;
            }
        }
        if (!found) {
            Map<String, Object> newEntry = new LinkedHashMap<>();
            newEntry.put("ip", ip);
            newEntry.put("first", now);
            newEntry.put("last", now);
            history.add(newEntry);
        }
        config.set(uuid + ".ip-history", history);

        try { config.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save playerdata: " + e.getMessage()); }
    }

    public String getLastIp(UUID uuid) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        return config.getString(uuid + ".last-ip", "");
    }

    public Map<String, long[]> getIpData(UUID uuid) {
        Map<String, long[]> result = new LinkedHashMap<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<String, Object>> history = (List<Map<String, Object>>) config.getList(uuid + ".ip-history");
        if (history == null) {
            String ip = config.getString(uuid + ".last-ip", "");
            long last = config.getLong(uuid + ".last-seen", 0);
            if (!ip.isEmpty()) result.put(ip, new long[]{last, last});
            return result;
        }
        for (Map<String, Object> entry : history) {
            String ip = (String) entry.get("ip");
            long first = entry.containsKey("first") ? ((Number) entry.get("first")).longValue() : 0;
            long last = entry.containsKey("last") ? ((Number) entry.get("last")).longValue() : 0;
            if (ip != null) result.put(ip, new long[]{first, last});
        }
        return result;
    }
}
