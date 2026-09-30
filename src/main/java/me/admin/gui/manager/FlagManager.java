package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FlagManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;

    public FlagManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "flags.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
    }

    public void setFlag(UUID uuid, String targetName, String moderator, String tag) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        config.set(uuid + ".name", targetName);
        config.set(uuid + ".tag", tag);
        config.set(uuid + ".moderator", moderator);
        config.set(uuid + ".date", System.currentTimeMillis());
        YamlPersistenceService.saveYamlNow(plugin, dataFile, config, "flags");
    }

    public void removeFlag(UUID uuid) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        config.set(uuid.toString(), null);
        YamlPersistenceService.saveYamlNow(plugin, dataFile, config, "flags");
    }

    public String getFlag(UUID uuid) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        return config.getString(uuid + ".tag");
    }

    public boolean hasFlag(UUID uuid) {
        return getFlag(uuid) != null;
    }

    public String getFlagModerator(UUID uuid) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        return config.getString(uuid + ".moderator", "?");
    }

    public Map<UUID, Map<String, String>> getAllFlags() {
        Map<UUID, Map<String, String>> result = new LinkedHashMap<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String tag = config.getString(key + ".tag");
                String name = config.getString(key + ".name");
                String moderator = config.getString(key + ".moderator", "?");
                if (tag != null) {
                    Map<String, String> data = new LinkedHashMap<>();
                    data.put("tag", tag);
                    data.put("name", name != null ? name : "?");
                    data.put("moderator", moderator);
                    result.put(uuid, data);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public String formatFlag(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        String lower = tag.toLowerCase();
        String prefix = switch (lower) {
            case "hacker", "cheater", "chair", "hack" -> "&c[H]";
            case "scammer", "scam" -> "&4[SCAM]";
            case "trusted", "trust" -> "&a[OK]";
            case "vip" -> "&6[VIP]";
            case "staff", "helper" -> "&b[STAFF]";
            case "griefer", "grief" -> "&c[GRIEF]";
            case "tosser", "toss" -> "&e[!]";
            default -> "&7";
        };
        return (prefix + tag).replace("&", "§");
    }
}
