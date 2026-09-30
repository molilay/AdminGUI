package me.admin.gui.integration;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPanelScopeTest {

    @Test
    void tokenScopesAreLeastPrivilegeAndExpire() {
        long now = 1_000L;
        assertTrue(WebPanel.scopeAllows(Set.of("health"), 0L, "health", now));
        assertFalse(WebPanel.scopeAllows(Set.of("health"), 0L, "audit", now));
        assertTrue(WebPanel.scopeAllows(Set.of("all"), now + 1L, "audit", now));
        assertFalse(WebPanel.scopeAllows(Set.of("all"), now, "health", now));
    }
}
