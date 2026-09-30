package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AppealManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final Map<Integer, AppealEntry> appeals = new LinkedHashMap<>();
    private int nextId = 1;

    public enum AppealStatus { PENDING, ACCEPTED, DENIED }

    public record AppealEntry(int id, String targetName, String submitter, String reason, long timestamp, AppealStatus status, String staffResponse) {
        public String getFormattedDate() {
            return TimeUtils.formatLogTime(timestamp);
        }
    }

    public AppealManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "appeals.yml");
        load();
    }

    public int submit(String targetName, String submitter, String reason) {
        AppealEntry entry = new AppealEntry(nextId, targetName, submitter, reason, System.currentTimeMillis(), AppealStatus.PENDING, "");
        appeals.put(nextId, entry);
        plugin.getAuditManager().record(null, submitter, "appeal.submit", targetName, null,
                "appeal=" + entry.id() + "; " + reason);

        String msg = "§8[§6Appeal§8] §f" + submitter + " §eподал апелляцию за §f" + targetName + "§e:\n §7" + reason;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.appeal.staff")) {
                staff.sendMessage(msg);
                staff.sendMessage(" §8§l→ §7/amgui appeals");
            }
        }

        plugin.getDiscordWebhook().ifPresent(d -> d.send("appeal", targetName, submitter, reason, "—"));

        nextId++;
        save();
        return entry.id();
    }

    public void accept(int id, String staffResponse) {
        accept(id, staffResponse, "System");
    }

    public void accept(int id, String staffResponse, String actor) {
        AppealEntry entry = appeals.get(id);
        if (entry != null) {
            me.admin.gui.utils.BanService.pardonProfile(entry.targetName());
            appeals.put(id, new AppealEntry(id, entry.targetName(), entry.submitter(), entry.reason(), entry.timestamp(), AppealStatus.ACCEPTED, staffResponse));
            plugin.getDatabaseManager().logPunishment("unban", "Appeal", entry.targetName(), "Апелляция принята: " + staffResponse, -1);
            plugin.getAuditManager().record(null, actor, "appeal.accept", entry.targetName(), null,
                    "appeal=" + id + "; " + staffResponse);
            save();
        }
    }

    public void deny(int id, String staffResponse) {
        deny(id, staffResponse, "System");
    }

    public void deny(int id, String staffResponse, String actor) {
        AppealEntry entry = appeals.get(id);
        if (entry != null) {
            appeals.put(id, new AppealEntry(id, entry.targetName(), entry.submitter(), entry.reason(), entry.timestamp(), AppealStatus.DENIED, staffResponse));
            plugin.getAuditManager().record(null, actor, "appeal.deny", entry.targetName(), null,
                    "appeal=" + id + "; " + staffResponse);
            save();
        }
    }

    public List<AppealEntry> getPending() {
        return appeals.values().stream()
                .filter(e -> e.status() == AppealStatus.PENDING)
                .sorted(Comparator.comparingLong(AppealEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<AppealEntry> getAll() {
        return appeals.values().stream()
                .sorted(Comparator.comparingLong(AppealEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public AppealEntry get(int id) { return appeals.get(id); }
    public int getPendingCount() { return (int) appeals.values().stream().filter(e -> e.status() == AppealStatus.PENDING).count(); }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        nextId = config.getInt("next-id", 1);
        List<Map<?, ?>> raw = config.getMapList("appeals");
        for (Map<?, ?> m : raw) {
            try {
                int id = (int) m.get("id");
                String targetName = (String) m.get("target");
                String submitter = (String) m.get("submitter");
                String reason = (String) m.get("reason");
                long timestamp = m.containsKey("timestamp") ? ((Number) m.get("timestamp")).longValue() : System.currentTimeMillis();
                AppealStatus status = m.containsKey("status") ? AppealStatus.valueOf((String) m.get("status")) : AppealStatus.PENDING;
                String staffResponse = m.containsKey("staff_response") ? (String) m.get("staff_response") : "";
                appeals.put(id, new AppealEntry(id, targetName, submitter, reason, timestamp, status, staffResponse));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (AppealEntry e : appeals.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("target", e.targetName());
            m.put("submitter", e.submitter());
            m.put("reason", e.reason());
            m.put("timestamp", e.timestamp());
            m.put("status", e.status().name());
            if (!e.staffResponse().isEmpty()) m.put("staff_response", e.staffResponse());
            list.add(m);
        }
        config.set("appeals", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "appeals");
    }
}
