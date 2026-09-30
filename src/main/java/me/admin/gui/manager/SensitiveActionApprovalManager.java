package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Two-person approval for high-impact file operations. State is intentionally memory-only. */
public final class SensitiveActionApprovalManager {

    public record Request(int id, String type, String payload, UUID requesterUuid, String requesterName,
                          long createdAt, long expiresAt) {}
    public record Decision(boolean allowed, String reason, Request request) {}

    private final AdvancedModeratorGUI plugin;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<Integer, Request> pending = new LinkedHashMap<>();

    public SensitiveActionApprovalManager(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public synchronized Request submit(Player requester, String type, String payload) {
        purgeExpired();
        int max = Math.clamp(plugin.getConfig().getInt("security.dual-approval.max-pending", 100), 5, 1000);
        while (pending.size() >= max) pending.remove(pending.keySet().iterator().next());
        long now = System.currentTimeMillis();
        long ttl = Math.clamp(plugin.getConfig().getLong("security.dual-approval.expire-seconds", 600), 30, 3600) * 1000L;
        Request request = new Request(sequence.incrementAndGet(), type, payload, requester.getUniqueId(),
                requester.getName(), now, now + ttl);
        pending.put(request.id(), request);
        plugin.getAuditManager().record(requester, "approval.request", type, null,
                "id=" + request.id() + "; payload=" + payload);
        return request;
    }

    public synchronized Decision approve(Player approver, int id) {
        purgeExpired();
        Request request = pending.get(id);
        if (request == null) return new Decision(false, "Запрос не найден или истёк", null);
        if (request.requesterUuid().equals(approver.getUniqueId())) {
            return new Decision(false, "Инициатор не может одобрить собственный запрос", request);
        }
        pending.remove(id);
        plugin.getAuditManager().record(approver, "approval.approve", request.type(), null,
                "id=" + id + "; requester=" + request.requesterName());
        return new Decision(true, "Одобрено", request);
    }

    public synchronized boolean reject(Player actor, int id) {
        purgeExpired();
        Request request = pending.remove(id);
        if (request == null) return false;
        plugin.getAuditManager().record(actor, "approval.reject", request.type(), null,
                "id=" + id + "; requester=" + request.requesterName());
        return true;
    }

    public synchronized List<Request> list() {
        purgeExpired();
        return pending.values().stream().sorted(Comparator.comparingLong(Request::createdAt)).toList();
    }

    public synchronized Optional<Request> get(int id) {
        purgeExpired();
        return Optional.ofNullable(pending.get(id));
    }

    public synchronized void clear() { pending.clear(); }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        List<Request> expired = new ArrayList<>();
        pending.values().removeIf(request -> {
            if (request.expiresAt() > now) return false;
            expired.add(request);
            return true;
        });
        expired.forEach(request -> plugin.getAuditManager().record(null, "approval.expire", request.type(), null,
                "id=" + request.id() + "; requester=" + request.requesterName()));
    }
}
