package me.admin.gui.manager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Memory-only escalation counters isolated by player, category and severity. */
public final class EscalationTracker {
    private record Bucket(UUID player, String category, int severity) {}
    private final Map<Bucket, Deque<Long>> history = new HashMap<>();

    public synchronized int record(UUID player, String category, int severity, long now, long windowMillis) {
        prune(now, windowMillis);
        Bucket bucket = new Bucket(player, category == null ? "general" : category, severity);
        Deque<Long> values = history.computeIfAbsent(bucket, ignored -> new ArrayDeque<>());
        values.addLast(now);
        return values.size();
    }

    public synchronized void clear(UUID player) {
        history.keySet().removeIf(bucket -> bucket.player().equals(player));
    }

    public synchronized void clearAll() { history.clear(); }

    public synchronized int bucketCount() { return history.size(); }

    private void prune(long now, long windowMillis) {
        long window = Math.max(1_000L, windowMillis);
        history.entrySet().removeIf(entry -> {
            Deque<Long> values = entry.getValue();
            while (!values.isEmpty() && now - values.peekFirst() > window) values.removeFirst();
            return values.isEmpty();
        });
    }
}
