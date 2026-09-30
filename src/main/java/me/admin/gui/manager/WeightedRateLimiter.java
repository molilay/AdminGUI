package me.admin.gui.manager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure weighted sliding-window limiter used after all authorization checks pass. */
public final class WeightedRateLimiter {
    public enum Outcome { ALLOWED, ACTOR_LIMIT, GLOBAL_LIMIT, BYPASS_HARD_CAP }
    private record Event(long timestamp, int weight) {}

    private final Map<UUID, Deque<Event>> actors = new HashMap<>();
    private final Deque<Event> global = new ArrayDeque<>();

    public synchronized Outcome acquire(UUID actor, int weight, boolean bypass, long now,
                                        long windowMillis, int actorLimit, int globalLimit, int bypassHardCap) {
        int safeWeight = Math.max(1, weight);
        prune(global, now, windowMillis);
        Deque<Event> actorEvents = actors.computeIfAbsent(actor, ignored -> new ArrayDeque<>());
        prune(actorEvents, now, windowMillis);
        int actorTotal = total(actorEvents);
        int globalTotal = total(global);

        if (globalTotal + safeWeight > Math.max(1, globalLimit)) return Outcome.GLOBAL_LIMIT;
        if (bypass) {
            if (actorTotal + safeWeight > Math.max(1, bypassHardCap)) return Outcome.BYPASS_HARD_CAP;
        } else if (actorTotal + safeWeight > Math.max(1, actorLimit)) {
            return Outcome.ACTOR_LIMIT;
        }
        Event event = new Event(now, safeWeight);
        actorEvents.addLast(event);
        global.addLast(event);
        return Outcome.ALLOWED;
    }

    private static void prune(Deque<Event> events, long now, long windowMillis) {
        long window = Math.max(1_000L, windowMillis);
        while (!events.isEmpty() && now - events.peekFirst().timestamp() > window) events.removeFirst();
    }

    private static int total(Deque<Event> events) {
        int result = 0;
        for (Event event : events) result += event.weight();
        return result;
    }
}
