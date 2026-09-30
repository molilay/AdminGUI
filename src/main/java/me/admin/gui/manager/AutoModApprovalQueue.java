package me.admin.gui.manager;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable YAML queue for destructive AutoMod actions awaiting human approval. */
public final class AutoModApprovalQueue {
    public record Request(int id, UUID targetId, String targetName, AutoModManager.Action action,
                          String reason, long duration, String ruleId, long timestamp) {}

    private final File file;
    private final Map<Integer, Request> requests = new LinkedHashMap<>();
    private int nextId = 1;

    public AutoModApprovalQueue(File file) {
        this.file = file;
        load();
    }

    public synchronized Request enqueue(UUID targetId, String targetName, AutoModManager.Action action,
                                        String reason, long duration, String ruleId) {
        requireApprovable(action);
        int assignedId = nextId;
        Request request = new Request(assignedId, targetId, targetName, action, reason, duration, ruleId,
                System.currentTimeMillis());
        requests.put(request.id(), request);
        nextId++;
        try {
            save();
        } catch (RuntimeException exception) {
            requests.remove(request.id());
            nextId = assignedId;
            throw exception;
        }
        return request;
    }

    public synchronized Request remove(int id) {
        Request removed = requests.remove(id);
        if (removed != null) {
            try {
                save();
            } catch (RuntimeException exception) {
                requests.put(id, removed);
                throw exception;
            }
        }
        return removed;
    }

    public synchronized void restore(Request request) {
        if (request == null || requests.containsKey(request.id())) return;
        requests.put(request.id(), request);
        nextId = Math.max(nextId, request.id() + 1);
        try {
            save();
        } catch (RuntimeException exception) {
            requests.remove(request.id());
            throw exception;
        }
    }

    public synchronized Request get(int id) { return requests.get(id); }

    public synchronized List<Request> list() {
        return requests.values().stream().sorted(Comparator.comparingInt(Request::id)).toList();
    }

    public synchronized List<Request> expireBefore(long cutoffTimestamp) {
        List<Request> expired = requests.values().stream()
                .filter(request -> request.timestamp() < cutoffTimestamp).toList();
        if (!expired.isEmpty()) {
            expired.forEach(request -> requests.remove(request.id()));
            try {
                save();
            } catch (RuntimeException exception) {
                expired.forEach(request -> requests.put(request.id(), request));
                throw exception;
            }
        }
        return expired;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, yaml.getInt("next-id", 1));
        for (Map<?, ?> row : yaml.getMapList("requests")) {
            try {
                AutoModManager.Action action = AutoModManager.Action.valueOf(String.valueOf(row.get("action")));
                requireApprovable(action);
                Request request = new Request(number(row.get("id")), UUID.fromString(String.valueOf(row.get("target-id"))),
                        String.valueOf(row.get("target-name")), action,
                        String.valueOf(row.get("reason")), longNumber(row.get("duration")),
                        String.valueOf(row.get("rule")), longNumber(row.get("timestamp")));
                requests.put(request.id(), request);
                nextId = Math.max(nextId, request.id() + 1);
            } catch (Exception ignored) {
                // Corrupt rows are skipped; no destructive action is ever inferred.
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Request request : requests.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", request.id());
            row.put("target-id", request.targetId().toString());
            row.put("target-name", request.targetName());
            row.put("action", request.action().name());
            row.put("reason", request.reason());
            row.put("duration", request.duration());
            row.put("rule", request.ruleId());
            row.put("timestamp", request.timestamp());
            rows.add(row);
        }
        yaml.set("requests", rows);
        try {
            java.nio.file.Path target = file.toPath().toAbsolutePath().normalize();
            java.nio.file.Path root = target.getParent();
            if (root == null) throw new IOException("Approval queue has no parent directory");
            YamlPersistenceService.atomicWrite(target, yaml.saveToString().getBytes(StandardCharsets.UTF_8), root);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save AutoMod approval queue", exception);
        }
    }

    private static int number(Object value) {
        long parsed = longNumber(value);
        if (parsed < 1L || parsed > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid id");
        return (int) parsed;
    }

    private static long longNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static void requireApprovable(AutoModManager.Action action) {
        if (action != AutoModManager.Action.BAN && action != AutoModManager.Action.TEMPBAN) {
            throw new IllegalArgumentException("only BAN and TEMPBAN can enter the approval queue");
        }
    }
}
