package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerNoteManager {

    private final AdvancedModeratorGUI plugin;
    private final File noteFile;

    public PlayerNoteManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.noteFile = new File(plugin.getDataFolder(), "notes.yml");
        if (!noteFile.exists()) {
            try {
                noteFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create notes.yml: " + e.getMessage());
            }
        }
    }

    public void addNote(UUID targetId, String targetName, String moderator, String note) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(noteFile);
        List<Map<String, Object>> notes = readMaps(config.getList(targetId.toString()));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("moderator", moderator);
        entry.put("note", note);
        entry.put("date", new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date()));
        entry.put("target", targetName);
        notes.add(entry);
        config.set(targetId.toString(), notes);
        YamlPersistenceService.saveYamlNow(plugin, noteFile, config, "player notes");
    }

    public List<String> getNotes(UUID targetId) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(noteFile);
        List<Map<String, Object>> raw = readMaps(config.getList(targetId.toString()));
        List<String> result = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            String moderator = String.valueOf(m.getOrDefault("moderator", "?"));
            String note = String.valueOf(m.getOrDefault("note", ""));
            String date = String.valueOf(m.getOrDefault("date", ""));
            result.add("§7[" + date + "] §f" + note + " §8(§7" + moderator + "§8)");
        }
        Collections.reverse(result);
        return result;
    }

    private static List<Map<String, Object>> readMaps(List<?> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (values == null) return result;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> source)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, item) -> copy.put(String.valueOf(key), item));
            result.add(copy);
        }
        return result;
    }
}
