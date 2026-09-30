package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecuritySimulatorTest {
    @Test void capsAndFloodChecksAreDeterministicAndUnicodeAware() {
        assertTrue(SecuritySimulator.matchesCaps("ПРОВЕРКА TEST", 5, 70));
        assertFalse(SecuritySimulator.matchesCaps("Обычный текст", 5, 70));
        assertTrue(SecuritySimulator.hasRepeatedCodePoint("ok😀😀😀😀", 4));
        assertFalse(SecuritySimulator.hasRepeatedCodePoint("😀😀😀", 4));
    }
}
