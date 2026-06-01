package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    private final AdvancedModeratorGUI plugin;
    private final Map<UUID, MuteEntry> muted = new ConcurrentHashMap<>();
    private final File muteFile;

    public MuteManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.muteFile = new File(plugin.getDataFolder(), "mutes.yml");
        loadMutes();
    }

    public void mute(Player target, String reason, String moderator, long durationSec) {
        long expires = durationSec > 0 ? System.currentTimeMillis() + (durationSec * 1000) : -1;
        muted.put(target.getUniqueId(), new MuteEntry(reason, moderator, expires));
        target.sendMessage("§cВы замьючены. Причина: " + reason +
                (durationSec > 0 ? " (" + formatDuration(durationSec) + ")" : " (навсегда)"));
        plugin.getDatabaseManager().logPunishment("mute", moderator, target.getName(), reason, durationSec);
        saveMutes();
    }

    public void unmute(UUID uuid) {
        muted.remove(uuid);
        saveMutes();
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry = muted.get(uuid);
        if (entry == null) return false;
        if (entry.expires > 0 && System.currentTimeMillis() > entry.expires) {
            muted.remove(uuid);
            saveMutes();
            return false;
        }
        return true;
    }

    public MuteEntry getMute(UUID uuid) {
        MuteEntry entry = muted.get(uuid);
        if (entry == null) return null;
        if (entry.expires > 0 && System.currentTimeMillis() > entry.expires) {
            muted.remove(uuid);
            saveMutes();
            return null;
        }
        return entry;
    }

    public MuteEntry getMuteEntry(UUID uuid) {
        return getMute(uuid);
    }

    public String formatRemaining(MuteEntry entry) {
        if (entry == null) return "—";
        if (entry.expires <= 0) return "&cНавсегда";
        long remaining = (entry.expires - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) return "&aИстек";
        return "&e" + TimeUtils.formatDuration(remaining);
    }

    public void checkExpirations() {
        long now = System.currentTimeMillis();
        muted.entrySet().removeIf(e -> e.getValue().expires > 0 && now > e.getValue().expires);
        saveMutes();
    }

    private void loadMutes() {
        if (!muteFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(muteFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String reason = config.getString(key + ".reason", "");
                String moderator = config.getString(key + ".moderator", "");
                long expires = config.getLong(key + ".expires", -1);
                muted.put(uuid, new MuteEntry(reason, moderator, expires));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveMutes() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, MuteEntry> e : muted.entrySet()) {
            config.set(e.getKey().toString() + ".reason", e.getValue().reason);
            config.set(e.getKey().toString() + ".moderator", e.getValue().moderator);
            config.set(e.getKey().toString() + ".expires", e.getValue().expires);
        }
        try {
            config.save(muteFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save mutes: " + ex.getMessage());
        }
    }

    private String formatDuration(long sec) {
        return TimeUtils.formatDuration(sec);
    }

    public record MuteEntry(String reason, String moderator, long expires) {}
}
