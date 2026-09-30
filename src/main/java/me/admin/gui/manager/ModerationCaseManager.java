package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModerationCaseManager {

    public enum Status { OPEN, INVESTIGATING, RESOLVED, REJECTED }
    public enum Priority { LOW, NORMAL, HIGH, CRITICAL }
    public enum ClaimResult { CLAIMED, ALREADY_OWNED, CONFLICT, NOT_FOUND, CLOSED }

    public record CaseEvent(long timestamp, String actor, String type, String details) {}

    public record ModerationCase(int id, UUID targetUuid, String targetName, String title,
                                 String createdBy, String assignedTo, Status status, Priority priority,
                                 long createdAt, long updatedAt, long dueAt, List<Integer> reportIds,
                                 List<Integer> evidenceIds, List<CaseEvent> events) {}

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final ConcurrentHashMap<Integer, ModerationCase> cases = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Long> deadlineNotifications = new ConcurrentHashMap<>();

    public ModerationCaseManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "cases.yml");
        load();
    }

    public synchronized int create(UUID targetUuid, String targetName, String title, String creator) {
        int maxOpen = Math.max(1, plugin.getConfig().getInt("moderation-cases.max-open-per-player", 20));
        long openCount = cases.values().stream().filter(c -> c.targetName().equalsIgnoreCase(targetName))
                .filter(c -> c.status() == Status.OPEN || c.status() == Status.INVESTIGATING).count();
        if (openCount >= maxOpen) return -1;
        int id = nextId.getAndIncrement();
        long now = System.currentTimeMillis();
        CaseEvent event = new CaseEvent(now, creator, "created", title);
        ModerationCase moderationCase = new ModerationCase(id, targetUuid, targetName, title, creator, "",
                Status.OPEN, Priority.NORMAL, now, now, deadlineFor(Priority.NORMAL, now), List.of(), List.of(), List.of(event));
        cases.put(id, moderationCase);
        save();
        plugin.getAuditManager().record(null, creator, "case.create", targetName, targetUuid, "case=" + id + "; " + title);
        return id;
    }

    public synchronized int createFromReport(ReportManager.ReportEntry report, UUID targetUuid) {
        int id = create(targetUuid, report.target(), "Жалоба #" + report.id() + ": " + report.reason(), report.reporter());
        if (id > 0) attachReport(id, report.id(), "System");
        return id;
    }

    public synchronized boolean assign(int id, String assignee, String actor) {
        return update(id, actor, "assigned", assignee, c -> copy(c, assignee, c.status(), c.priority(), c.reportIds(), c.evidenceIds(), c.events()));
    }

    /** Compare-and-claim operation used by the triage inbox after its confirmation screen. */
    public synchronized ClaimResult claim(int id, String assignee, String expectedOwner, boolean takeover, String actor) {
        ModerationCase current = cases.get(id);
        if (current == null) return ClaimResult.NOT_FOUND;
        if (current.status() != Status.OPEN && current.status() != Status.INVESTIGATING) return ClaimResult.CLOSED;
        if (!current.assignedTo().equalsIgnoreCase(expectedOwner == null ? "" : expectedOwner)) return ClaimResult.CONFLICT;
        if (current.assignedTo().equalsIgnoreCase(assignee)) return ClaimResult.ALREADY_OWNED;
        if (!current.assignedTo().isBlank() && !takeover) return ClaimResult.CONFLICT;
        return assign(id, assignee, actor) ? ClaimResult.CLAIMED : ClaimResult.NOT_FOUND;
    }

    public synchronized boolean setStatus(int id, Status status, String actor, String reason) {
        return update(id, actor, "status", status.name() + ": " + reason,
                c -> copy(c, c.assignedTo(), status, c.priority(), c.reportIds(), c.evidenceIds(), c.events()));
    }

    public synchronized boolean addNote(int id, String actor, String note) {
        return update(id, actor, "note", note, c -> c);
    }

    public synchronized boolean setPriority(int id, Priority priority, String actor) {
        return update(id, actor, "priority", priority.name(), c -> withDue(copy(c, c.assignedTo(), c.status(), priority,
                c.reportIds(), c.evidenceIds(), c.events()), plugin.getConfig().getBoolean("moderation-cases.reset-deadline-on-priority-change", true)
                ? deadlineFor(priority, System.currentTimeMillis()) : c.dueAt()));
    }

    public synchronized boolean setDeadline(int id, long dueAt, String actor) {
        long safeDueAt = dueAt <= 0 ? 0 : Math.max(System.currentTimeMillis() + 60_000L, dueAt);
        return update(id, actor, "deadline", Long.toString(safeDueAt), c -> withDue(c, safeDueAt));
    }

    public synchronized int createFromTemplate(UUID targetUuid, String targetName, String templateId, String creator) {
        String base = "moderation-cases.templates." + templateId;
        String title = plugin.getConfig().getString(base + ".title");
        if (title == null || title.isBlank()) return -1;
        int id = create(targetUuid, targetName, title.replace("%player%", targetName), creator);
        if (id <= 0) return id;
        try {
            Priority priority = Priority.valueOf(plugin.getConfig().getString(base + ".priority", "NORMAL").toUpperCase(Locale.ROOT));
            setPriority(id, priority, creator);
            long hours = Math.max(1, plugin.getConfig().getLong(base + ".deadline-hours", deadlineHours(priority)));
            setDeadline(id, System.currentTimeMillis() + hours * 3600_000L, creator);
        } catch (IllegalArgumentException ignored) {
            // Keep safe NORMAL defaults when a template priority is invalid.
        }
        return id;
    }

    public synchronized boolean attachReport(int id, int reportId, String actor) {
        return update(id, actor, "report", Integer.toString(reportId), c -> {
            List<Integer> ids = new ArrayList<>(c.reportIds());
            if (!ids.contains(reportId)) ids.add(reportId);
            return copy(c, c.assignedTo(), c.status(), c.priority(), ids, c.evidenceIds(), c.events());
        });
    }

    public synchronized boolean attachEvidence(int id, int evidenceId, String actor) {
        return update(id, actor, "evidence", Integer.toString(evidenceId), c -> {
            List<Integer> ids = new ArrayList<>(c.evidenceIds());
            if (!ids.contains(evidenceId)) ids.add(evidenceId);
            return copy(c, c.assignedTo(), c.status(), c.priority(), c.reportIds(), ids, c.events());
        });
    }

    public synchronized boolean attachEvidenceToLatestOpen(String targetName, int evidenceId, String actor) {
        ModerationCase openCase = latestOpenCase(targetName);
        return openCase != null && attachEvidence(openCase.id(), evidenceId, actor);
    }

    /** Adds a punishment to the most recently updated open case for the target. */
    public synchronized boolean recordPunishment(String targetName, String actor, String type,
                                                 String reason, long duration) {
        ModerationCase openCase = latestOpenCase(targetName);
        if (openCase == null) return false;
        String details = type + ": " + reason + (duration > 0 ? "; duration=" + duration : "");
        return update(openCase.id(), actor, "punishment", details, c -> c);
    }

    private ModerationCase latestOpenCase(String targetName) {
        return cases.values().stream()
                .filter(c -> c.targetName().equalsIgnoreCase(targetName))
                .filter(c -> c.status() == Status.OPEN || c.status() == Status.INVESTIGATING)
                .max(Comparator.comparingLong(ModerationCase::updatedAt))
                .orElse(null);
    }

    private boolean update(int id, String actor, String type, String details,
                           java.util.function.UnaryOperator<ModerationCase> operation) {
        ModerationCase current = cases.get(id);
        if (current == null) return false;
        ModerationCase changed = operation.apply(current);
        List<CaseEvent> events = new ArrayList<>(changed.events());
        long now = System.currentTimeMillis();
        events.add(new CaseEvent(now, actor, type, details));
        ModerationCase result = new ModerationCase(changed.id(), changed.targetUuid(), changed.targetName(),
                changed.title(), changed.createdBy(), changed.assignedTo(), changed.status(), changed.priority(), changed.createdAt(), now,
                changed.dueAt(), List.copyOf(changed.reportIds()), List.copyOf(changed.evidenceIds()), List.copyOf(events));
        cases.put(id, result);
        save();
        plugin.getAuditManager().record(null, actor, "case." + type, result.targetName(), result.targetUuid(),
                "case=" + id + "; " + details);
        return true;
    }

    private static ModerationCase copy(ModerationCase c, String assigned, Status status,
                                       Priority priority,
                                       List<Integer> reports, List<Integer> evidence, List<CaseEvent> events) {
        return new ModerationCase(c.id(), c.targetUuid(), c.targetName(), c.title(), c.createdBy(), assigned,
                status, priority, c.createdAt(), c.updatedAt(), c.dueAt(), List.copyOf(reports), List.copyOf(evidence), List.copyOf(events));
    }

    private static ModerationCase withDue(ModerationCase c, long dueAt) {
        return new ModerationCase(c.id(), c.targetUuid(), c.targetName(), c.title(), c.createdBy(), c.assignedTo(),
                c.status(), c.priority(), c.createdAt(), c.updatedAt(), dueAt, c.reportIds(), c.evidenceIds(), c.events());
    }

    public ModerationCase get(int id) { return cases.get(id); }

    public List<ModerationCase> getAll() {
        return cases.values().stream().sorted(Comparator.comparingLong(ModerationCase::updatedAt).reversed()).toList();
    }

    public List<ModerationCase> getOpen() {
        return getAll().stream().filter(c -> c.status() == Status.OPEN || c.status() == Status.INVESTIGATING).toList();
    }

    public List<ModerationCase> getOverdue() {
        long now = System.currentTimeMillis();
        return getOpen().stream().filter(c -> c.dueAt() > 0 && c.dueAt() < now).toList();
    }

    public void notifyOverdue() {
        long now = System.currentTimeMillis();
        long repeat = Math.clamp(plugin.getConfig().getLong("moderation-cases.overdue-reminder-minutes", 60), 5, 1440) * 60_000L;
        List<ModerationCase> overdue = getOverdue();
        java.util.Set<Integer> activeIds = overdue.stream().map(ModerationCase::id).collect(java.util.stream.Collectors.toSet());
        deadlineNotifications.keySet().removeIf(id -> !activeIds.contains(id));
        for (ModerationCase value : overdue) {
            Long last = deadlineNotifications.get(value.id());
            if (last != null && now - last < repeat) continue;
            deadlineNotifications.put(value.id(), now);
            for (org.bukkit.entity.Player staff : plugin.getServer().getOnlinePlayers()) {
                boolean assigned = !value.assignedTo().isBlank() && staff.getName().equalsIgnoreCase(value.assignedTo());
                if (assigned || plugin.getConfig().getBoolean("moderation-cases.notify-all-staff-overdue", true)
                        && staff.hasPermission("amgui.cases")) {
                    staff.sendMessage("§8[§cCases§8] §cПросрочено дело #" + value.id() + " §f" + value.targetName()
                            + " §8— §7" + value.title());
                }
            }
        }
    }

    public List<ModerationCase> search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return getAll();
        return getAll().stream().filter(c -> Integer.toString(c.id()).equals(normalized)
                || c.targetName().toLowerCase(Locale.ROOT).contains(normalized)
                || c.title().toLowerCase(Locale.ROOT).contains(normalized)
                || c.assignedTo().toLowerCase(Locale.ROOT).contains(normalized)
                || c.status().name().toLowerCase(Locale.ROOT).contains(normalized)
                || c.priority().name().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId.get());
        for (ModerationCase c : cases.values()) {
            String base = "cases." + c.id();
            yaml.set(base + ".target-uuid", c.targetUuid() == null ? "" : c.targetUuid().toString());
            yaml.set(base + ".target-name", c.targetName());
            yaml.set(base + ".title", c.title());
            yaml.set(base + ".created-by", c.createdBy());
            yaml.set(base + ".assigned-to", c.assignedTo());
            yaml.set(base + ".status", c.status().name());
            yaml.set(base + ".priority", c.priority().name());
            yaml.set(base + ".created-at", c.createdAt());
            yaml.set(base + ".updated-at", c.updatedAt());
            yaml.set(base + ".due-at", c.dueAt());
            yaml.set(base + ".report-ids", c.reportIds());
            yaml.set(base + ".evidence-ids", c.evidenceIds());
            List<String> events = c.events().stream().map(e -> e.timestamp() + "|" + encode(e.actor()) + "|"
                    + encode(e.type()) + "|" + encode(e.details())).toList();
            yaml.set(base + ".events", events);
        }
        YamlPersistenceService.queueYaml(plugin, dataFile, yaml, "moderation cases");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        nextId.set(Math.max(1, yaml.getInt("next-id", 1)));
        ConfigurationSection section = yaml.getConfigurationSection("cases");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String base = "cases." + key;
                String uuid = yaml.getString(base + ".target-uuid", "");
                List<CaseEvent> events = new ArrayList<>();
                for (String raw : yaml.getStringList(base + ".events")) {
                    String[] parts = raw.split("\\|", -1);
                    if (parts.length == 4) events.add(new CaseEvent(Long.parseLong(parts[0]), decode(parts[1]), decode(parts[2]), decode(parts[3])));
                }
                cases.put(id, new ModerationCase(id, uuid.isBlank() ? null : UUID.fromString(uuid),
                        yaml.getString(base + ".target-name", "?"), yaml.getString(base + ".title", ""),
                        yaml.getString(base + ".created-by", "?"), yaml.getString(base + ".assigned-to", ""),
                        Status.valueOf(yaml.getString(base + ".status", "OPEN").toUpperCase(Locale.ROOT)),
                        Priority.valueOf(yaml.getString(base + ".priority", "NORMAL").toUpperCase(Locale.ROOT)),
                        yaml.getLong(base + ".created-at"), yaml.getLong(base + ".updated-at"),
                        yaml.getLong(base + ".due-at", deadlineForLoaded(yaml, base)),
                        List.copyOf(yaml.getIntegerList(base + ".report-ids")),
                        List.copyOf(yaml.getIntegerList(base + ".evidence-ids")), List.copyOf(events)));
            } catch (Exception e) {
                plugin.getLogger().warning("Пропущено повреждённое дело " + key + ": " + e.getMessage());
            }
        }
    }

    private static String encode(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private long deadlineFor(Priority priority, long from) { return from + deadlineHours(priority) * 3600_000L; }
    private long deadlineHours(Priority priority) {
        return Math.clamp(plugin.getConfig().getLong("moderation-cases.deadline-hours." + priority.name().toLowerCase(Locale.ROOT),
                switch (priority) { case LOW -> 168; case NORMAL -> 72; case HIGH -> 24; case CRITICAL -> 6; }), 1, 24 * 365L);
    }
    private long deadlineForLoaded(YamlConfiguration yaml, String base) {
        long created = yaml.getLong(base + ".created-at", System.currentTimeMillis());
        Priority priority;
        try { priority = Priority.valueOf(yaml.getString(base + ".priority", "NORMAL").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { priority = Priority.NORMAL; }
        return deadlineFor(priority, created);
    }

    private static String decode(String value) {
        return new String(java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
    }
}
