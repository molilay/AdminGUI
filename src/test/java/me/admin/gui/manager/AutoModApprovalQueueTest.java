package me.admin.gui.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoModApprovalQueueTest {
    @TempDir Path tempDir;

    @Test
    void persistsRemovesAndExpiresWithoutExecutingAnything() {
        Path file = tempDir.resolve("automod-pending.yml");
        UUID target = UUID.randomUUID();
        AutoModApprovalQueue queue = new AutoModApprovalQueue(file.toFile());
        var first = queue.enqueue(target, "Target", AutoModManager.Action.BAN, "reason", 0L, "rule");
        assertEquals(1, first.id());

        AutoModApprovalQueue reloaded = new AutoModApprovalQueue(file.toFile());
        assertEquals(1, reloaded.list().size());
        assertNotNull(reloaded.get(first.id()));
        assertNotNull(reloaded.remove(first.id()));
        assertTrue(reloaded.list().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> reloaded.enqueue(target, "Target",
                AutoModManager.Action.KICK, "reason", 0L, "rule"));

        reloaded.enqueue(target, "Target", AutoModManager.Action.TEMPBAN, "reason", 3600L, "rule");
        assertEquals(1, reloaded.expireBefore(System.currentTimeMillis() + 1L).size());
        assertTrue(reloaded.list().isEmpty());
    }
}
