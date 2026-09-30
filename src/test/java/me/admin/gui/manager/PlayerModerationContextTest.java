package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerModerationContextTest {

    @Test
    void normalizesSnapshotAndMissingSnapshotNeverGrantsBypass() {
        UUID id = UUID.randomUUID();
        PlayerModerationContext context = new PlayerModerationContext(
                id, "Alice", "World_Nether", "MoDeRaToR", true, false, 123L);
        assertEquals("world_nether", context.world());
        assertEquals("moderator", context.primaryGroup());

        PlayerModerationContext missing = PlayerModerationContext.restrictive(id);
        assertFalse(missing.autoModBypass());
        assertFalse(missing.antiRaidBypass());
        assertEquals("default", missing.primaryGroup());
    }
}
