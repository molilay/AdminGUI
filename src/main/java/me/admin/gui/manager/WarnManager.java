package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarnManager {

    private final AdvancedModeratorGUI plugin;
    private final Map<UUID, List<WarnEntry>> warns = new ConcurrentHashMap<>();
    private final File warnFile;

    public WarnManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.warnFile = new File(plugin.getDataFolder(), "warns.yml");
        loadWarns();
    }

    public int getMaxWarns() {
        return plugin.getConfig().getInt("warn.max-warns", 3);
    }

    public void warn(Player target, String reason, String moderator) {
        warns.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>())
                .add(new WarnEntry(reason, moderator, System.currentTimeMillis()));

        target.sendMessage("§c⚠ Вы получили предупреждение: " + reason);

        plugin.getDatabaseManager().logPunishment("warn", moderator, target.getName(), reason, -1);
        plugin.getVaultIntegration().ifPresent(v -> v.fine(target, plugin.getConfig().getDouble("warn.fine", 50.0)));

        int count = getWarnCount(target.getUniqueId());
        int maxWarns = getMaxWarns();
        if (count >= maxWarns) {
            String banReason = "Авто-бан: " + maxWarns + "/" + maxWarns + " предупреждений";
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), banReason, null, moderator);
            target.kickPlayer("§cВы забанены.\n§7Причина: " + banReason);
            plugin.getDatabaseManager().logPunishment("ban", moderator, target.getName(), banReason, -1);
            plugin.getDiscordWebhook().ifPresent(w -> w.send("ban", target.getName(), moderator, banReason, "Авто-бан"));
            warns.remove(target.getUniqueId());
        }
        saveWarns();
    }

    public int getWarnCount(UUID uuid) {
        return warns.getOrDefault(uuid, List.of()).size();
    }

    public List<WarnEntry> getWarns(UUID uuid) {
        List<WarnEntry> list = warns.get(uuid);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    public void removeWarn(UUID uuid, int index) {
        List<WarnEntry> list = warns.get(uuid);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            if (list.isEmpty()) warns.remove(uuid);
        }
        saveWarns();
    }

    public void checkExpirations() {
        long now = System.currentTimeMillis();
        warns.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(w -> now - w.timestamp > 604800000L); // 7 days
            return entry.getValue().isEmpty();
        });
        saveWarns();
    }

    private void loadWarns() {
        if (!warnFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(warnFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<WarnEntry> entries = new ArrayList<>();
                ConfigurationSection sec = config.getConfigurationSection(key);
                if (sec != null) {
                    for (String idx : sec.getKeys(false)) {
                        String reason = sec.getString(idx + ".reason", "");
                        String moderator = sec.getString(idx + ".moderator", "");
                        long timestamp = sec.getLong(idx + ".timestamp", System.currentTimeMillis());
                        entries.add(new WarnEntry(reason, moderator, timestamp));
                    }
                }
                if (!entries.isEmpty()) warns.put(uuid, entries);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveWarns() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, List<WarnEntry>> e : warns.entrySet()) {
            List<WarnEntry> entries = e.getValue();
            for (int i = 0; i < entries.size(); i++) {
                String path = e.getKey().toString() + "." + i;
                config.set(path + ".reason", entries.get(i).reason);
                config.set(path + ".moderator", entries.get(i).moderator);
                config.set(path + ".timestamp", entries.get(i).timestamp);
            }
        }
        try {
            config.save(warnFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save warns: " + ex.getMessage());
        }
    }

    public record WarnEntry(String reason, String moderator, long timestamp) {}
}
