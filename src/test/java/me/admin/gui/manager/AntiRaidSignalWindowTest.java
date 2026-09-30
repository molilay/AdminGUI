package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AntiRaidSignalWindowTest {
    private static final AntiRaidSignalWindow.Settings SETTINGS = new AntiRaidSignalWindow.Settings(
            20_000L, 3, 3, 3, 60_000L, 604_800_000L, 0.6D, 10_000L);

    @Test
    void reconnectDoesNotInflateUniqueWindow() {
        AntiRaidSignalWindow window = new AntiRaidSignalWindow();
        UUID player = UUID.randomUUID();
        window.record(player, 100_000L, 0L, "203.0.113.1", SETTINGS);
        var result = window.record(player, 101_000L, 0L, "203.0.113.1", SETTINGS);
        assertFalse(result.triggered());
        assertEquals(1, result.snapshot().uniqueJoins());
        assertEquals(1, result.snapshot().largestSubnet());
    }

    @Test
    void groupsIpv4By24AndIpv6By64AndIgnoresUnknown() {
        assertEquals("203.0.113.0/24", AntiRaidSignalWindow.networkGroup("203.0.113.99").orElseThrow());
        assertEquals(AntiRaidSignalWindow.networkGroup("2001:db8:1:2::1"),
                AntiRaidSignalWindow.networkGroup("2001:db8:1:2:ffff::9"));
        assertTrue(AntiRaidSignalWindow.networkGroup("unknown").isEmpty());
        assertTrue(AntiRaidSignalWindow.networkGroup(" ").isEmpty());
        assertTrue(AntiRaidSignalWindow.networkGroup("999.1.1.1").isEmpty());
    }

    @Test
    void trustedAccountsDoNotTripMassJoinButRemainVisible() {
        AntiRaidSignalWindow window = new AntiRaidSignalWindow();
        long now = 1_000_000_000L;
        for (int i = 0; i < 3; i++) {
            var detection = window.record(UUID.randomUUID(), now + i, now - 700_000_000L,
                    "198.51.100." + (i + 1), SETTINGS);
            assertFalse(detection.triggered());
        }
        var snapshot = window.current(now + 5L, SETTINGS.windowMillis());
        assertEquals(3, snapshot.uniqueJoins());
        assertEquals(0, snapshot.untrustedJoins());
        assertEquals(0, snapshot.largestSubnet());
    }

    @Test
    void triggerIsExplainableAndHysteresisPreventsRepeatedAlert() {
        AntiRaidSignalWindow window = new AntiRaidSignalWindow();
        long now = 10_000L;
        window.record(UUID.randomUUID(), now, 0L, "198.51.100.1", SETTINGS);
        window.record(UUID.randomUUID(), now + 1, 0L, "198.51.100.2", SETTINGS);
        var trigger = window.record(UUID.randomUUID(), now + 2, 0L, "198.51.100.3", SETTINGS);
        assertTrue(trigger.triggered());
        assertEquals("mass-join", trigger.trigger());
        assertTrue(trigger.explanation().contains("untrusted=3/3"));

        var repeated = window.record(UUID.randomUUID(), now + 3, 0L, "198.51.100.4", SETTINGS);
        assertFalse(repeated.triggered());
    }
}
