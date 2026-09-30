package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegexGuardTest {
    @Test
    void acceptsBoundedProductionPatterns() {
        assertTrue(RegexGuard.validate("(?:https?://|www\\.)\\S+", 192).safe());
        assertTrue(RegexGuard.validate("\\b(?:\\w+\\.)+(?:com|net)\\b", 192).safe());
    }

    @Test
    void rejectsKnownBacktrackingShapesAndBackreferences() {
        assertFalse(RegexGuard.validate("(a+)+$", 192).safe());
        assertFalse(RegexGuard.validate("(a|aa)+$", 192).safe());
        assertFalse(RegexGuard.validate("(.*foo.*bar.*)", 192).safe());
        assertFalse(RegexGuard.validate("(a)\\1", 192).safe());
        assertFalse(RegexGuard.validate("(?<=secret)text", 192).safe());
    }
}
