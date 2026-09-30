package me.admin.gui.manager.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModerationActionServiceTest {

    @Test
    void mapsEveryDangerousCallbackToItsDedicatedPermission() {
        assertEquals("amgui.kick", ModerationActionService.Action.fromSecurityAction("kick").permission());
        assertEquals("amgui.ban", ModerationActionService.Action.fromSecurityAction("ipban").permission());
        assertEquals("amgui.inventory.edit",
                ModerationActionService.Action.fromSecurityAction("inventory-clear").permission());
        assertEquals("amgui.authme.sensitive",
                ModerationActionService.Action.fromSecurityAction("authme").permission());
        assertNull(ModerationActionService.Action.fromSecurityAction("history-read"));
    }
}
