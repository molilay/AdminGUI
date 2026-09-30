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
import java.util.stream.Collectors;

public final class ReportManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final Map<Integer, ReportEntry> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> reportCooldowns = new HashMap<>();
    private int nextId = 1;

    public record ReportEntry(int id, String reporter, String target, String reason, long timestamp, boolean resolved, String category, boolean archived) {
        public ReportEntry(int id, String reporter, String target, String reason, long timestamp, boolean resolved) {
            this(id, reporter, target, reason, timestamp, resolved, "other", false);
        }

        public ReportEntry(int id, String reporter, String target, String reason, long timestamp, boolean resolved, String category) {
            this(id, reporter, target, reason, timestamp, resolved, category, false);
        }

        public String getFormattedDate() {
            return TimeUtils.formatLogTime(timestamp);
        }
    }

    public ReportManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "reports.yml");
        load();
        archiveOldReports();
    }

    public int submit(String reporter, String target, String reason, String category) {
        UUID reporterUUID = Bukkit.getPlayerExact(reporter) != null ? Bukkit.getPlayerExact(reporter).getUniqueId() : null;

        if (reporterUUID != null && hasCooldown(reporterUUID)) {
            Player p = Bukkit.getPlayer(reporterUUID);
            if (p != null) {
                long remaining = (getCooldownMs() - (System.currentTimeMillis() - reportCooldowns.get(reporterUUID))) / 1000;
                p.sendMessage("§cВы можете отправлять жалобы раз в " + (getCooldownMs() / 1000) + " секунд. Осталось: " + remaining + "с.");
            }
            return -1;
        }

        if (reporterUUID != null) {
            reportCooldowns.put(reporterUUID, System.currentTimeMillis());
        }

        ReportEntry entry = new ReportEntry(nextId, reporter, target, reason, System.currentTimeMillis(), false, category);
        reports.put(nextId, entry);

        plugin.getAuditManager().record(null, reporter, "report.submit", target, null,
                "report=" + entry.id() + "; category=" + category + "; " + reason);
        if (plugin.getConfig().getBoolean("moderation-cases.auto-create-from-reports", true)) {
            org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(target);
            plugin.getModerationCaseManager().createFromReport(entry, cached == null ? null : cached.getUniqueId());
        }

        String msg = "§8[§6Report§8] §f" + reporter + " §eпожаловался на §f" + target + " §8(§7" + category + "§8)§e:\n §7" + reason;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.report.staff")) {
                staff.sendMessage(msg);
                staff.sendMessage(" §8§l→ §7/reports или §7/amgui reports");
            }
        }

        plugin.getDiscordWebhook().ifPresent(d -> d.send("report", target, reporter, "[" + category + "] " + reason, "—"));

        nextId++;
        save();
        return entry.id();
    }

    public boolean hasCooldown(UUID playerUUID) {
        Long last = reportCooldowns.get(playerUUID);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < getCooldownMs();
    }

    private long getCooldownMs() {
        return plugin.getConfig().getLong("reports.cooldown-seconds", 60) * 1000L;
    }

    public void resolve(int id) {
        resolve(id, "System");
    }

    public void resolve(int id, String actor) {
        ReportEntry entry = reports.get(id);
        if (entry != null) {
            reports.put(id, new ReportEntry(id, entry.reporter(), entry.target(), entry.reason(), entry.timestamp(), true, entry.category(), entry.archived()));
            plugin.getAuditManager().record(null, actor, "report.resolve", entry.target(), null, "report=" + id);
            save();
        }
    }

    public void remove(int id) {
        remove(id, "System");
    }

    public void remove(int id, String actor) {
        ReportEntry removed = reports.remove(id);
        if (removed != null) {
            plugin.getAuditManager().record(null, actor, "report.remove", removed.target(), null, "report=" + id);
        }
        save();
    }

    public void archiveOldReports() {
        long cutoff = System.currentTimeMillis() - (plugin.getConfig().getLong("reports.archive-after-days", 7) * 86400L * 1000L);
        boolean changed = false;
        for (Map.Entry<Integer, ReportEntry> e : reports.entrySet()) {
            ReportEntry r = e.getValue();
            if (r.resolved() && !r.archived() && r.timestamp() < cutoff) {
                e.setValue(new ReportEntry(r.id(), r.reporter(), r.target(), r.reason(), r.timestamp(), true, r.category(), true));
                changed = true;
            }
        }
        if (changed) save();
    }

    public List<ReportEntry> getActive() {
        return reports.values().stream()
                .filter(e -> !e.resolved() && !e.archived())
                .sorted(Comparator.comparingLong(ReportEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<ReportEntry> getArchived() {
        return reports.values().stream()
                .filter(ReportEntry::archived)
                .sorted(Comparator.comparingLong(ReportEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<ReportEntry> getResolved() {
        return reports.values().stream()
                .filter(e -> e.resolved() && !e.archived())
                .sorted(Comparator.comparingLong(ReportEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<ReportEntry> getAll() {
        return reports.values().stream()
                .sorted(Comparator.comparingLong(ReportEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public ReportEntry get(int id) {
        return reports.get(id);
    }

    public int getActiveCount() {
        return (int) reports.values().stream().filter(e -> !e.resolved() && !e.archived()).count();
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        nextId = config.getInt("next-id", 1);
        List<Map<?, ?>> raw = config.getMapList("reports");
        for (Map<?, ?> m : raw) {
            try {
                int id = (int) m.get("id");
                String reporter = (String) m.get("reporter");
                String target = (String) m.get("target");
                String reason = (String) m.get("reason");
                long timestamp = m.containsKey("timestamp") ? ((Number) m.get("timestamp")).longValue() : System.currentTimeMillis();
                boolean resolved = m.containsKey("resolved") && (boolean) m.get("resolved");
                String category = m.containsKey("category") ? (String) m.get("category") : "other";
                boolean archived = m.containsKey("archived") && (boolean) m.get("archived");
                reports.put(id, new ReportEntry(id, reporter, target, reason, timestamp, resolved, category, archived));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ReportEntry e : reports.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("reporter", e.reporter());
            m.put("target", e.target());
            m.put("reason", e.reason());
            m.put("timestamp", e.timestamp());
            m.put("resolved", e.resolved());
            m.put("category", e.category());
            m.put("archived", e.archived());
            list.add(m);
        }
        config.set("reports", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "reports");
    }
}
