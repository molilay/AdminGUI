package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks focused moderator work sessions and stores completed summaries in YAML. */
public final class ModerationSessionManager {

    public record SessionAction(long timestamp, String action, String details) {}
    public record SessionSummary(UUID id, UUID moderatorUuid, String moderatorName, UUID targetUuid, String targetName,
                                 long startedAt, long endedAt, String conclusion, List<SessionAction> actions) {}

    private static final class ActiveSession {
        private final UUID id = UUID.randomUUID();
        private final UUID moderatorUuid;
        private final String moderatorName;
        private final UUID targetUuid;
        private final String targetName;
        private final long startedAt = System.currentTimeMillis();
        private final List<SessionAction> actions = new ArrayList<>();

        private ActiveSession(Player moderator, OfflinePlayer target) {
            moderatorUuid = moderator.getUniqueId();
            moderatorName = moderator.getName();
            targetUuid = target.getUniqueId();
            targetName = target.getName() == null ? "?" : target.getName();
        }
    }

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final Map<UUID, ActiveSession> active = new ConcurrentHashMap<>();
    private final List<SessionSummary> completed = new ArrayList<>();

    public ModerationSessionManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "moderation-sessions.yml");
        load();
    }

    public boolean start(Player moderator, OfflinePlayer target) {
        if (active.containsKey(moderator.getUniqueId())) return false;
        ActiveSession session = new ActiveSession(moderator, target);
        session.actions.add(new SessionAction(System.currentTimeMillis(), "session.start", "Начато расследование"));
        active.put(moderator.getUniqueId(), session);
        plugin.getAuditManager().record(moderator, "investigation.session-start", session.targetName, session.targetUuid,
                "session=" + session.id);
        return true;
    }

    public SessionSummary finish(Player moderator, String conclusion) {
        ActiveSession session = active.remove(moderator.getUniqueId());
        if (session == null) return null;
        synchronized (session.actions) {
            session.actions.add(new SessionAction(System.currentTimeMillis(), "session.finish", conclusion));
            SessionSummary summary = new SessionSummary(session.id, session.moderatorUuid, session.moderatorName,
                    session.targetUuid, session.targetName, session.startedAt, System.currentTimeMillis(), conclusion,
                    List.copyOf(session.actions));
            synchronized (completed) {
                completed.add(summary);
                int limit = Math.clamp(plugin.getConfig().getInt("moderation-sessions.keep-last", 200), 10, 2000);
                completed.sort(Comparator.comparingLong(SessionSummary::endedAt).reversed());
                if (completed.size() > limit) completed.subList(limit, completed.size()).clear();
                save();
            }
            plugin.getAuditManager().record(moderator, "investigation.session-finish", session.targetName, session.targetUuid,
                    "session=" + session.id + "; actions=" + summary.actions().size() + "; conclusion=" + conclusion);
            return summary;
        }
    }

    public void recordAudit(UUID actorUuid, UUID targetUuid, String action, String details) {
        if (actorUuid == null) return;
        ActiveSession session = active.get(actorUuid);
        if (session == null) return;
        if (targetUuid != null && !session.targetUuid.equals(targetUuid)) return;
        if (action.startsWith("investigation.session-")) return;
        synchronized (session.actions) {
            int max = Math.clamp(plugin.getConfig().getInt("moderation-sessions.max-actions", 100), 10, 500);
            if (session.actions.size() < max) session.actions.add(new SessionAction(System.currentTimeMillis(), action, details));
        }
    }

    public boolean isActive(Player moderator) { return active.containsKey(moderator.getUniqueId()); }

    public String activeTarget(Player moderator) {
        ActiveSession session = active.get(moderator.getUniqueId());
        return session == null ? "" : session.targetName;
    }

    public long activeDuration(Player moderator) {
        ActiveSession session = active.get(moderator.getUniqueId());
        return session == null ? 0L : Math.max(0L, System.currentTimeMillis() - session.startedAt);
    }

    public List<SessionSummary> getCompletedFor(UUID targetUuid) {
        synchronized (completed) {
            return completed.stream().filter(value -> value.targetUuid().equals(targetUuid))
                    .sorted(Comparator.comparingLong(SessionSummary::endedAt).reversed()).toList();
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SessionSummary summary : completed) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", summary.id().toString());
            row.put("moderator-uuid", summary.moderatorUuid().toString());
            row.put("moderator", summary.moderatorName());
            row.put("target-uuid", summary.targetUuid().toString());
            row.put("target", summary.targetName());
            row.put("started-at", summary.startedAt());
            row.put("ended-at", summary.endedAt());
            row.put("conclusion", summary.conclusion());
            row.put("actions", summary.actions().stream().map(action -> Map.of(
                    "timestamp", action.timestamp(), "action", action.action(), "details", action.details())).toList());
            rows.add(row);
        }
        yaml.set("sessions", rows);
        YamlPersistenceService.queueYaml(plugin, dataFile, yaml, "moderation sessions");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (Map<?, ?> row : yaml.getMapList("sessions")) {
            try {
                List<SessionAction> actions = new ArrayList<>();
                Object rawActions = row.get("actions");
                if (rawActions instanceof List<?> list) {
                    for (Object value : list) {
                        if (!(value instanceof Map<?, ?> action)) continue;
                        actions.add(new SessionAction(number(action.get("timestamp")), text(action.get("action")), text(action.get("details"))));
                    }
                }
                completed.add(new SessionSummary(UUID.fromString(text(row.get("id"))),
                        UUID.fromString(text(row.get("moderator-uuid"))), text(row.get("moderator")),
                        UUID.fromString(text(row.get("target-uuid"))), text(row.get("target")),
                        number(row.get("started-at")), number(row.get("ended-at")), text(row.get("conclusion")), List.copyOf(actions)));
            } catch (Exception e) {
                plugin.getLogger().warning("Пропущена повреждённая модераторская сессия: " + e.getMessage());
            }
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (NumberFormatException ignored) { return 0L; }
    }
}
