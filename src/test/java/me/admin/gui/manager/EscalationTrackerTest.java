package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscalationTrackerTest {
    @Test
    void isolatesCategoryAndSeverityBuckets() {
        EscalationTracker tracker = new EscalationTracker();
        UUID player = UUID.randomUUID();
        assertEquals(1, tracker.record(player, "spam", 1, 1_000L, 10_000L));
        assertEquals(2, tracker.record(player, "spam", 1, 2_000L, 10_000L));
        assertEquals(1, tracker.record(player, "advertising", 1, 2_000L, 10_000L));
        assertEquals(1, tracker.record(player, "spam", 3, 2_000L, 10_000L));
    }

    @Test
    void expiresOldViolations() {
        EscalationTracker tracker = new EscalationTracker();
        UUID player = UUID.randomUUID();
        tracker.record(player, "spam", 1, 1_000L, 5_000L);
        assertEquals(1, tracker.record(player, "spam", 1, 7_000L, 5_000L));
    }
}
