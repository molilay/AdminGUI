package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PaginatedGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class StaffActivityManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final List<ActivityEntry> logs = new ArrayList<>();

    public record ActivityEntry(String staffName, String action, String target, String reason, long duration, long timestamp) {
        public String getFormattedDate() { return TimeUtils.formatLogTime(timestamp); }
    }

    public StaffActivityManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "staff-activity.yml");
        load();
    }

    public void log(Player staff, String action, String target, String reason, long duration) {
        ActivityEntry entry = new ActivityEntry(staff.getName(), action, target, reason, duration, System.currentTimeMillis());
        logs.add(entry);
        if (logs.size() > 10000) logs.remove(0);
        save();
        plugin.getAuditManager().record(staff, "staff." + action, target, null, reason + "; duration=" + duration);
    }

    public void log(String staffName, String action, String target, String reason, long duration) {
        ActivityEntry entry = new ActivityEntry(staffName, action, target, reason, duration, System.currentTimeMillis());
        logs.add(entry);
        if (logs.size() > 10000) logs.remove(0);
        save();
        plugin.getAuditManager().record(null, staffName, "staff." + action, target, null,
                reason + "; duration=" + duration);
    }

    public List<ActivityEntry> getLogs() {
        return logs.stream().sorted(Comparator.comparingLong(ActivityEntry::timestamp).reversed()).collect(Collectors.toList());
    }

    public List<ActivityEntry> getLogsForStaff(String staffName) {
        return logs.stream().filter(e -> e.staffName().equalsIgnoreCase(staffName)).sorted(Comparator.comparingLong(ActivityEntry::timestamp).reversed()).collect(Collectors.toList());
    }

    public Map<String, Long> getActionCounts(UUID staffUuid) {
        String name = Bukkit.getOfflinePlayer(staffUuid).getName();
        if (name == null) return Collections.emptyMap();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ActivityEntry e : logs) {
            if (e.staffName().equalsIgnoreCase(name)) {
                counts.merge(e.action(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ActivityEntry e : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("staff", e.staffName());
            m.put("action", e.action());
            m.put("target", e.target());
            m.put("reason", e.reason());
            m.put("duration", e.duration());
            m.put("timestamp", e.timestamp());
            list.add(m);
        }
        config.set("logs", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "staff activity");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<?, ?>> raw = config.getMapList("logs");
        for (Map<?, ?> m : raw) {
            try {
                logs.add(new ActivityEntry(
                        (String) m.get("staff"),
                        (String) m.get("action"),
                        (String) m.get("target"),
                        (String) m.get("reason"),
                        m.containsKey("duration") ? ((Number) m.get("duration")).longValue() : 0,
                        m.containsKey("timestamp") ? ((Number) m.get("timestamp")).longValue() : 0
                ));
            } catch (Exception ignored) {}
        }
    }

    public static class StaffActivityGUI extends PaginatedGUI {
        private final StaffActivityManager sam;
        private List<ActivityEntry> entries;

        public StaffActivityGUI(AdvancedModeratorGUI plugin, Player viewer) {
            super(plugin, viewer);
            this.sam = plugin.getStaffActivityManager().orElse(null);
        }

        @Override
        public String getTitle() { return "§8Активность персонала"; }

        @Override
        public void buildContent() {
            contentItems.clear();
            if (sam == null) return;
            entries = sam.getLogs();
            for (ActivityEntry e : entries) {
                contentItems.add(new ItemBuilder(Material.PAPER)
                        .name("&f" + e.staffName() + " &7→ &f" + (e.target() != null ? e.target() : "—"))
                        .lore(
                                "&7Действие: &f" + e.action(),
                                "&7Причина: &f" + (e.reason() != null ? e.reason() : "—"),
                                "&7Время: &f" + e.getFormattedDate()
                        ).build());
            }
        }

        @Override
        public void onClick(int slot) {
            if (slot >= 45) { handlePaginatedClick(slot); return; }
        }
    }
}
