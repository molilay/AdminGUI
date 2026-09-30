package me.admin.gui.manager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Constant-time, non-blocking bounded ingress used by the audit journal. */
final class AuditIngress<T> {
    private final ArrayBlockingQueue<T> queue;
    private final AtomicLong rejected = new AtomicLong();

    AuditIngress(int capacity) { queue = new ArrayBlockingQueue<>(Math.max(1, capacity)); }

    boolean offer(T value) {
        boolean accepted = queue.offer(value);
        if (!accepted) rejected.incrementAndGet();
        return accepted;
    }

    T poll(long timeout, TimeUnit unit) throws InterruptedException { return queue.poll(timeout, unit); }
    boolean isEmpty() { return queue.isEmpty(); }
    int size() { return queue.size(); }
    int capacity() { return queue.size() + queue.remainingCapacity(); }
    long rejected() { return rejected.get(); }
}
