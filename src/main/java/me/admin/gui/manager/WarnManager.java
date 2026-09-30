package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.security.ModerationActionService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WarnManager {

    private static final Set<String> AUTOMATED_SOURCES = Set.of("automod", "blacklist", "autowarn");

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

    public UUID warn(Player target, String reason, String moderator) {
        return warn(target, reason, moderator, -1, plugin.getConfigManager().getWarnDefaultPoints());
    }

    public UUID warn(Player target, String reason, String moderator, long durationSec, int points) {
        long expiresAt = durationSec > 0 ? System.currentTimeMillis() + durationSec * 1000 : -1;
        UUID warnId = UUID.randomUUID();
        synchronized (warns) {
            List<WarnEntry> list = warns.computeIfAbsent(target.getUniqueId(),
                    ignored -> Collections.synchronizedList(new ArrayList<>()));
            list.add(new WarnEntry(warnId, reason, moderator, System.currentTimeMillis(), expiresAt, points));
        }

        target.sendMessage("§c⚠ Вы получили предупреждение: " + reason);
        plugin.getDatabaseManager().logPunishment("warn", moderator, target.getName(), reason, -1);
        plugin.getVaultIntegration().ifPresent(v -> v.fine(target, plugin.getConfig().getDouble("warn.fine", 50.0)));

        Player staff = moderator == null ? null : Bukkit.getPlayerExact(moderator);
        if (staff != null) {
            plugin.getConfigManager().sendPunishmentTitle(staff, "warn", target.getName());
            plugin.getConfigManager().playPunishmentSound(staff, "warn");
        }
        if (target.hasPermission("amgui.staffchat")) {
            String message = "§8[§c⚠§8] §f" + moderator + " §cпредупредил сотрудника §f" + target.getName();
            Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("amgui.admin"))
                    .forEach(p -> p.sendMessage(message));
            plugin.getDatabaseManager().logPunishment("staffpunish-warn", moderator, target.getName(), reason, -1);
        }

        int count = getWarnCount(target.getUniqueId());
        if (count >= getMaxWarns()) {
            String banReason = "Лимит предупреждений: " + count + "/" + getMaxWarns();
            if (attemptDangerousEscalation(target, moderator, banReason)) {
                clearAfterEscalation(target.getUniqueId());
                return null;
            }
        }

        if (plugin.getConfigManager().isWarnAutoBan()) {
            int pointsTotal = getActivePoints(target.getUniqueId(), System.currentTimeMillis());
            if (pointsTotal >= plugin.getConfigManager().getWarnPointsThreshold()) {
                String banReason = "Лимит предупреждений: " + pointsTotal + " баллов";
                if (attemptDangerousEscalation(target, moderator, banReason)) {
                    clearAfterEscalation(target.getUniqueId());
                    return null;
                }
            }
        }
        saveWarns();
        return warnId;
    }

    public int getWarnCount(UUID uuid) {
        List<WarnEntry> list = warns.get(uuid);
        return list == null ? 0 : list.size();
    }

    public List<WarnEntry> getWarns(UUID uuid) {
        List<WarnEntry> list = warns.get(uuid);
        if (list == null) return List.of();
        synchronized (list) { return new ArrayList<>(list); }
    }

    public void removeWarn(UUID uuid, int index) {
        synchronized (warns) {
            List<WarnEntry> list = warns.get(uuid);
            if (list != null && index >= 0 && index < list.size()) {
                list.remove(index);
                if (list.isEmpty()) warns.remove(uuid);
            }
        }
        saveWarns();
    }

    public boolean removeWarn(UUID uuid, UUID warnId) {
        boolean removed = false;
        synchronized (warns) {
            List<WarnEntry> list = warns.get(uuid);
            if (list != null) {
                removed = list.removeIf(entry -> entry.id().equals(warnId));
                if (list.isEmpty()) warns.remove(uuid);
            }
        }
        if (removed) saveWarns();
        return removed;
    }

    public boolean hasWarn(UUID uuid, UUID warnId) {
        List<WarnEntry> list = warns.get(uuid);
        if (list == null) return false;
        synchronized (list) { return list.stream().anyMatch(entry -> entry.id().equals(warnId)); }
    }

    public void checkExpirations() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        synchronized (warns) {
            var iterator = warns.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                changed |= entry.getValue().removeIf(warn -> warn.expiresAt() > 0 && now > warn.expiresAt());
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) saveWarns();
    }

    private int getActivePoints(UUID uuid, long now) {
        int total = 0;
        List<WarnEntry> list = warns.get(uuid);
        if (list == null) return 0;
        synchronized (list) {
            for (WarnEntry entry : list) {
                if (entry.expiresAt() <= 0 || now <= entry.expiresAt()) total += entry.points();
            }
        }
        return total;
    }

    /**
     * WARN is non-destructive. Turning it into BAN is a separate manual action:
     * automated sources are always rejected, and staff needs both ban rights and
     * the dedicated escalation capability.
     */
    private boolean attemptDangerousEscalation(Player target, String moderator, String banReason) {
        String source = moderator == null ? "" : moderator.toLowerCase(Locale.ROOT);
        Player staff = moderator == null ? null : Bukkit.getPlayerExact(moderator);
        if (AUTOMATED_SOURCES.contains(source) || staff == null || !staff.isOnline()) {
            plugin.getAuditManager().record(null, "warn.escalation-denied", target.getName(),
                    target.getUniqueId(), "source=" + moderator + "; reason=human-approval-required");
            notifyReview(target, "требуется ручное одобрение");
            return false;
        }
        if (!staff.hasPermission("amgui.warn.escalate.ban")) {
            plugin.getAuditManager().record(staff, "warn.escalation-denied", target.getName(),
                    target.getUniqueId(), "missing=amgui.warn.escalate.ban");
            staff.sendMessage("§eЛимит предупреждений достигнут; автобан отклонён без amgui.warn.escalate.ban.");
            return false;
        }
        var decision = ModerationActionService.execute(plugin, staff, target, ModerationActionService.Action.BAN);
        if (!decision.allowed()) {
            staff.sendMessage("§cЭскалация WARN → BAN отклонена: " + decision.reason());
            return false;
        }

        me.admin.gui.utils.BanService.banProfile(target, banReason, null, moderator);
        target.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize("§cВы забанены.\n§7Причина: " + banReason));
        plugin.getDatabaseManager().logPunishment("ban", moderator, target.getName(), banReason, -1);
        plugin.getDiscordWebhook().ifPresent(webhook -> webhook.send("ban", target.getName(), moderator,
                banReason, "WARN escalation"));
        plugin.getAuditManager().record(staff, "warn.escalation-approved", target.getName(),
                target.getUniqueId(), "permission=amgui.warn.escalate.ban");
        return true;
    }

    private void notifyReview(Player target, String reason) {
        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("amgui.automod.approve"))
                .forEach(p -> p.sendMessage("§8[§cWARN§8] §e" + target.getName()
                        + " достиг лимита: бан не выполнен, " + reason + "."));
    }

    private void clearAfterEscalation(UUID uuid) {
        synchronized (warns) { warns.remove(uuid); }
        saveWarns();
    }

    private void loadWarns() {
        if (!warnFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(warnFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<WarnEntry> entries = Collections.synchronizedList(new ArrayList<>());
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null) {
                    for (String index : section.getKeys(false)) {
                        String idValue = section.getString(index + ".id", "");
                        UUID id;
                        try { id = UUID.fromString(idValue); }
                        catch (IllegalArgumentException error) { id = UUID.randomUUID(); }
                        entries.add(new WarnEntry(id, section.getString(index + ".reason", ""),
                                section.getString(index + ".moderator", ""),
                                section.getLong(index + ".timestamp", System.currentTimeMillis()),
                                section.getLong(index + ".expiresAt", -1),
                                section.getInt(index + ".points", 1)));
                    }
                }
                if (!entries.isEmpty()) warns.put(uuid, entries);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveWarns() {
        synchronized (warns) {
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, List<WarnEntry>> row : warns.entrySet()) {
                List<WarnEntry> entries = row.getValue();
                synchronized (entries) {
                    for (int i = 0; i < entries.size(); i++) {
                        String path = row.getKey() + "." + i;
                        WarnEntry entry = entries.get(i);
                        config.set(path + ".id", entry.id().toString());
                        config.set(path + ".reason", entry.reason());
                        config.set(path + ".moderator", entry.moderator());
                        config.set(path + ".timestamp", entry.timestamp());
                        config.set(path + ".expiresAt", entry.expiresAt());
                        config.set(path + ".points", entry.points());
                    }
                }
            }
            YamlPersistenceService.queueYaml(plugin, warnFile, config, "warnings");
        }
    }

    public record WarnEntry(UUID id, String reason, String moderator, long timestamp, long expiresAt, int points) {}
}
