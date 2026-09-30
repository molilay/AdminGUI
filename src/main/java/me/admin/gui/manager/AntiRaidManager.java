package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** UUID-deduplicated raid detector with subnet signals and reversible lockdown. */
public final class AntiRaidManager {

    public record RaidIncident(long timestamp, String trigger, int joins, int newPlayers, int sameIp, String actor) {}

    private final AdvancedModeratorGUI plugin;
    private final File incidentFile;
    private final AntiRaidSignalWindow signals = new AntiRaidSignalWindow();
    private final List<RaidIncident> incidents = new ArrayList<>();
    private final Map<UUID, Long> chatNotices = new ConcurrentHashMap<>();
    private final AtomicLong lockdownUntil = new AtomicLong();
    private volatile AntiRaidSignalWindow.Snapshot lastSnapshot = AntiRaidSignalWindow.Snapshot.empty();

    public AntiRaidManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.incidentFile = new File(plugin.getDataFolder(), "antiraid-incidents.yml");
        load();
    }

    public boolean isEnabled() { return plugin.getConfig().getBoolean("antiraid.enabled", true); }

    public boolean isAlertOnly() {
        String mode = plugin.getConfig().getString("antiraid.mode", "lockdown");
        return plugin.getConfig().getBoolean("antiraid.alert-only", false)
                || "alert-only".equalsIgnoreCase(mode) || "monitor".equalsIgnoreCase(mode);
    }

    public synchronized void onJoin(Player player, String ip) {
        if (!isEnabled() || player.hasPermission("amgui.antiraid.bypass")) return;
        long now = System.currentTimeMillis();
        AntiRaidSignalWindow.Detection detection = signals.record(player.getUniqueId(), now,
                player.getFirstPlayed(), ip, settings());
        lastSnapshot = detection.snapshot();
        if (!detection.triggered()) return;

        String explainedTrigger = detection.trigger() + " [" + detection.explanation() + "]";
        if (isAlertOnly()) {
            recordIncident("alert-only:" + explainedTrigger, "AntiRaid", detection.snapshot());
            notifyStaff("§8[§eAntiRaid§8] §eПодозрительные входы: §f" + detection.explanation()
                    + " §8(§7режим только уведомлений§8)");
            plugin.getAuditManager().record(null, "AntiRaid", "antiraid.alert", "server", null,
                    "trigger=" + detection.trigger() + "; " + detection.explanation());
            plugin.getDiscordWebhook().ifPresent(webhook -> webhook.send("antiraid-alert", "server", "AntiRaid",
                    detection.trigger(), detection.explanation()));
            return;
        }

        if (!isLockdown()) {
            long duration = Math.clamp(plugin.getConfig().getLong("antiraid.lockdown-seconds", 180), 30, 3600);
            enableLockdown("AntiRaid", explainedTrigger, duration, detection.snapshot());
        }
    }

    public boolean shouldBlockChat(Player player) {
        return shouldBlockChat(player.hasPermission("amgui.antiraid.bypass"));
    }

    /** Async-safe chat decision using only a permission value captured on the main thread. */
    public boolean shouldBlockChat(boolean antiRaidBypass) {
        return isLockdown() && plugin.getConfig().getBoolean("antiraid.block-chat", true) && !antiRaidBypass;
    }

    public boolean shouldBlockJoin(Player player) {
        if (!isLockdown() || !plugin.getConfig().getBoolean("antiraid.block-new-joins", false)
                || player.hasPermission("amgui.antiraid.bypass")) return false;
        long trustedAge = trustedAgeMillis();
        return player.getFirstPlayed() <= 0L || System.currentTimeMillis() - player.getFirstPlayed() < trustedAge;
    }

    public void notifyChatBlocked(Player player) {
        long now = System.currentTimeMillis();
        Long last = chatNotices.put(player.getUniqueId(), now);
        if (last == null || now - last > 3000L) {
            player.sendMessage("§8[§cAntiRaid§8] §cЧат временно закрыт из-за подозрительной активности входов.");
        }
    }

    public void enableManual(Player actor, long seconds) {
        long duration = Math.clamp(seconds, 30, 86400);
        AntiRaidSignalWindow.Snapshot snapshot = currentSnapshot();
        enableLockdown(actor.getName(), "manual", duration, snapshot);
    }

    private synchronized void enableLockdown(String actor, String trigger, long seconds,
                                             AntiRaidSignalWindow.Snapshot snapshot) {
        lockdownUntil.set(System.currentTimeMillis() + seconds * 1000L);
        recordIncident(trigger, actor, snapshot);
        notifyStaff("§8[§cAntiRaid§8] §cВключён защитный режим §7(" + trigger + ", " + seconds + "с)");
        plugin.getAuditManager().record(null, actor, "antiraid.lockdown-enable", "server", null,
                "trigger=" + trigger + "; seconds=" + seconds + "; unique=" + snapshot.uniqueJoins()
                        + "; untrusted=" + snapshot.untrustedJoins() + "; new=" + snapshot.newPlayers()
                        + "; network=" + snapshot.largestSubnet() + "; ignoredIp=" + snapshot.ignoredAddresses());
        plugin.getDiscordWebhook().ifPresent(webhook -> webhook.send("antiraid", "server", actor,
                "Lockdown: " + trigger + ", unique=" + snapshot.uniqueJoins(), seconds + "s"));
    }

    private synchronized void recordIncident(String trigger, String actor, AntiRaidSignalWindow.Snapshot snapshot) {
        RaidIncident incident = new RaidIncident(System.currentTimeMillis(), trigger, snapshot.uniqueJoins(),
                snapshot.newPlayers(), snapshot.largestSubnet(), actor);
        incidents.add(0, incident);
        int keep = Math.clamp(plugin.getConfig().getInt("antiraid.keep-incidents", 100), 10, 1000);
        if (incidents.size() > keep) incidents.subList(keep, incidents.size()).clear();
        save();
    }

    public void disable(Player actor) {
        lockdownUntil.set(0L);
        chatNotices.clear();
        notifyStaff("§8[§cAntiRaid§8] §aЗащитный режим выключен " + actor.getName());
        plugin.getAuditManager().record(actor, "antiraid.lockdown-disable", "server", null, "");
    }

    private void notifyStaff(String message) {
        Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission("amgui.antiraid"))
                .forEach(staff -> staff.sendMessage(message));
    }

    public boolean isLockdown() {
        long until = lockdownUntil.get();
        if (until > 0L && until <= System.currentTimeMillis()) lockdownUntil.compareAndSet(until, 0L);
        return lockdownUntil.get() > System.currentTimeMillis();
    }

    public long remainingSeconds() {
        return isLockdown() ? Math.max(0L, (lockdownUntil.get() - System.currentTimeMillis()) / 1000L) : 0L;
    }

    public int recentJoinCount() { return currentSnapshot().uniqueJoins(); }

    public AntiRaidSignalWindow.Snapshot currentSnapshot() {
        long window = Math.clamp(plugin.getConfig().getLong("antiraid.window-seconds", 20), 5, 300) * 1000L;
        lastSnapshot = signals.current(System.currentTimeMillis(), window);
        return lastSnapshot;
    }

    public synchronized List<RaidIncident> getIncidents() { return List.copyOf(incidents); }

    private AntiRaidSignalWindow.Settings settings() {
        long window = Math.clamp(plugin.getConfig().getLong("antiraid.window-seconds", 20), 5, 300) * 1000L;
        int joinsLimit = Math.clamp(plugin.getConfig().getInt("antiraid.joins-threshold", 15), 3, 500);
        int newLimit = Math.clamp(plugin.getConfig().getInt("antiraid.new-player-threshold", 10), 3, 500);
        int subnetLimit = Math.clamp(plugin.getConfig().getInt("antiraid.same-ip-threshold", 8), 3, 100);
        long newAge = Math.clamp(plugin.getConfig().getLong("antiraid.new-player-age-seconds", 60), 0, 604800) * 1000L;
        long trustedAge = trustedAgeMillis();
        double hysteresis = Math.clamp(plugin.getConfig().getDouble("antiraid.hysteresis-ratio", 0.6D), 0.1D, 0.95D);
        long cooldown = Math.clamp(plugin.getConfig().getLong("antiraid.trigger-cooldown-seconds", 120), 0, 3600) * 1000L;
        return new AntiRaidSignalWindow.Settings(window, joinsLimit, newLimit, subnetLimit,
                newAge, trustedAge, hysteresis, cooldown);
    }

    private long trustedAgeMillis() {
        return Math.clamp(plugin.getConfig().getLong("antiraid.trusted-account-age-seconds", 604800),
                60, 31_536_000) * 1000L;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RaidIncident incident : incidents) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", incident.timestamp());
            row.put("trigger", incident.trigger());
            row.put("joins", incident.joins());
            row.put("new-players", incident.newPlayers());
            row.put("same-ip", incident.sameIp());
            row.put("actor", incident.actor());
            rows.add(row);
        }
        yaml.set("incidents", rows);
        YamlPersistenceService.queueYaml(plugin, incidentFile, yaml, "antiraid incidents");
    }

    private void load() {
        if (!incidentFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(incidentFile);
        for (Map<?, ?> row : yaml.getMapList("incidents")) {
            try {
                incidents.add(new RaidIncident(number(row.get("timestamp")), String.valueOf(row.get("trigger")),
                        (int) number(row.get("joins")), (int) number(row.get("new-players")),
                        (int) number(row.get("same-ip")), String.valueOf(row.get("actor"))));
            } catch (Exception ignored) {
                // A corrupt historical row must not disable current protection.
            }
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception ignored) { return 0L; }
    }
}
