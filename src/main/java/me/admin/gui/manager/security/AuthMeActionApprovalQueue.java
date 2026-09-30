package me.admin.gui.manager.security;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Memory-only two-person gate. No credential or recovery token is ever stored here. */
public final class AuthMeActionApprovalQueue {

    public enum Action { FORCE_LOGIN, FORCE_LOGOUT, RECOVERY_TOKEN }
    public enum Status { REQUESTED, APPROVED, OWN_REQUEST, FULL }
    public record Decision(Status status, UUID requester, long expiresAt) {}

    private record Key(UUID target, Action action) {}
    private record Request(UUID requester, long expiresAt) {}

    private final Map<Key, Request> pending = new LinkedHashMap<>();
    private final int capacity;

    public AuthMeActionApprovalQueue() { this(256); }

    AuthMeActionApprovalQueue(int capacity) {
        this.capacity = Math.clamp(capacity, 8, 4096);
    }

    public synchronized Decision requestOrApprove(UUID actor, UUID target, Action action,
                                                  long now, long ttlMillis) {
        purge(now);
        Key key = new Key(target, action);
        Request request = pending.get(key);
        if (request == null) {
            if (pending.size() >= capacity) {
                return new Decision(Status.FULL, null, 0L);
            }
            long expiresAt = now + Math.clamp(ttlMillis, 30_000L, 3_600_000L);
            pending.put(key, new Request(actor, expiresAt));
            return new Decision(Status.REQUESTED, actor, expiresAt);
        }
        if (request.requester().equals(actor)) {
            return new Decision(Status.OWN_REQUEST, request.requester(), request.expiresAt());
        }
        pending.remove(key);
        return new Decision(Status.APPROVED, request.requester(), request.expiresAt());
    }

    public synchronized int size(long now) {
        purge(now);
        return pending.size();
    }

    private void purge(long now) {
        Iterator<Request> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt() <= now) iterator.remove();
        }
    }
}
