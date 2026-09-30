package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoModNormalizationTest {

    @Test
    void normalizesUnicodeWidthAndCase() {
        assertEquals("foo bar", AutoModManager.normalizeForMatching("ＦＯＯ BAR"));
    }

    @Test
    void removesInvisibleFormattingCharacters() {
        assertEquals("testvalue", AutoModManager.normalizeForMatching("test\u200Bvalue"));
    }

    @Test
    void collapsesPunctuationAndWhitespace() {
        assertEquals("hello world", AutoModManager.normalizeForMatching("  hello___world!!!  "));
    }

    @Test
    void acceptsNullInput() {
        assertEquals("", AutoModManager.normalizeForMatching(null));
    }

    @Test
    void foldsMixedCyrillicAndLatinHomoglyphs() {
        assertEquals(AutoModManager.normalizeForMatching("мудак"),
                AutoModManager.normalizeForMatching("мyдaк"));
    }
}
