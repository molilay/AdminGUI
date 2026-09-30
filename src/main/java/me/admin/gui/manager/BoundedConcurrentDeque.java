package me.admin.gui.manager;

import java.util.ArrayDeque;
import java.util.List;

/** Small synchronized ring with immutable snapshots and a reloadable bound. */
final class BoundedConcurrentDeque<T> {
    private final ArrayDeque<T> values = new ArrayDeque<>();
    private volatile int maximum;

    BoundedConcurrentDeque(int maximum) { this.maximum = Math.max(1, maximum); }

    synchronized void addLast(T value) {
        values.addLast(value);
        trim();
    }

    synchronized List<T> snapshot() { return List.copyOf(values); }

    synchronized void resize(int maximum) {
        this.maximum = Math.max(1, maximum);
        trim();
    }

    synchronized int size() { return values.size(); }

    private void trim() {
        while (values.size() > maximum) values.removeFirst();
    }
}
