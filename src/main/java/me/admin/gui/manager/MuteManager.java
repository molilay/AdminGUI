package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    private final AdvancedModeratorGUI plugin;
    private final Map<UUID, MuteEntry> muted = new ConcurrentHashMap<>();
    private final File muteFile;
    private final YamlPersistenceService persistence;

    public MuteManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.muteFile = new File(plugin.getDataFolder(), "mutes.yml");
        this.persistence = YamlPersistenceService.forPlugin(plugin);
        loadMutes();
    }

    public void mute(Player target, String reason, String moderator, long durationSec) {
        long expires = durationSec > 0 ? System.currentTimeMillis() + (durationSec * 1000) : -1;
        synchronized (muted) { muted.put(target.getUniqueId(), new MuteEntry(reason, moderator, expires, target.getName())); }
        target.sendMessage("§cВы замьючены. Причина: " + reason +
                (durationSec > 0 ? " (" + formatDuration(durationSec) + ")" : " (навсегда)"));
        plugin.getDatabaseManager().logPunishment("mute", moderator, target.getName(), reason, durationSec);

        Player staff = Bukkit.getPlayerExact(moderator);
        if (staff != null) {
            plugin.getConfigManager().sendPunishmentTitle(staff, "mute", target.getName());
            plugin.getConfigManager().playPunishmentSound(staff, "mute");
        }

        if (target.hasPermission("amgui.staffchat")) {
            String msg = "§8[§c⚠§8] §f" + moderator + " §cнаказал сотрудника §f" + target.getName();
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("amgui.admin"))
                .forEach(p -> p.sendMessage(msg));
            plugin.getDatabaseManager().logPunishment("staffpunish-mute", moderator, target.getName(), reason, durationSec);
        }

        saveMutes();
    }

    public void unmute(UUID uuid) {
        synchronized (muted) { muted.remove(uuid); }
        saveMutes();
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry;
        boolean expired = false;
        synchronized (muted) {
            entry = muted.get(uuid);
            if (entry != null && entry.expires > 0 && System.currentTimeMillis() > entry.expires) {
                muted.remove(uuid);
                entry = null;
                expired = true;
            }
        }
        if (expired) saveMutes();
        return entry != null;
    }

    public MuteEntry getMute(UUID uuid) {
        MuteEntry entry;
        boolean expired = false;
        synchronized (muted) {
            entry = muted.get(uuid);
            if (entry != null && entry.expires > 0 && System.currentTimeMillis() > entry.expires) {
                muted.remove(uuid);
                entry = null;
                expired = true;
            }
        }
        if (expired) saveMutes();
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
        boolean changed = false;
        long now = System.currentTimeMillis();
        synchronized (muted) {
            var it = muted.entrySet().iterator();
            while (it.hasNext()) {
                MuteEntry entry = it.next().getValue();
                if (entry.expires > 0 && now > entry.expires) {
                    it.remove();
                    changed = true;
                }
            }
        }
        if (changed) saveMutes();
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
                    String targetName = config.getString(key + ".targetName", "");
                    muted.put(uuid, new MuteEntry(reason, moderator, expires, targetName));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveMutes() {
        String snapshot;
        synchronized (muted) {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, MuteEntry> e : muted.entrySet()) {
                config.set(e.getKey().toString() + ".reason", e.getValue().reason);
                config.set(e.getKey().toString() + ".moderator", e.getValue().moderator);
                config.set(e.getKey().toString() + ".expires", e.getValue().expires);
                config.set(e.getKey().toString() + ".targetName", e.getValue().targetName);
            }
            snapshot = config.saveToString();
        }
        if (!persistence.save(muteFile.toPath(), snapshot))
            plugin.getLogger().warning("Failed to enqueue mutes.yml save");
    }

    private String formatDuration(long sec) {
        return TimeUtils.formatDuration(sec);
    }

    public Map<UUID, MuteEntry> getMutedEntries() {
        boolean changed;
        Map<UUID, MuteEntry> snapshot;
        synchronized (muted) {
            long now = System.currentTimeMillis();
            changed = muted.entrySet().removeIf(e -> e.getValue().expires > 0 && now > e.getValue().expires);
            snapshot = new java.util.HashMap<>(muted);
        }
        if (changed) saveMutes();
        return snapshot;
    }

    /** Flushes all plugin YAML snapshots, including mutes, before shutdown/backup. */
    public void flush() { persistence.flush(); }

    public record MuteEntry(String reason, String moderator, long expires, String targetName) {}
}
