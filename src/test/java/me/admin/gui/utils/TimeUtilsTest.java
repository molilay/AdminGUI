package me.admin.gui.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilsTest {

    @Test
    void parsesCombinedRussianAndEnglishDurations() {
        assertEquals(93_784, TimeUtils.parseDuration("1д 2h 3м 4s"));
        assertEquals(5_400, TimeUtils.parseDuration("1H30M"));
    }

    @Test
    void rejectsPartiallyValidAndOverflowingValues() {
        assertEquals(0, TimeUtils.parseDuration("через 1ч"));
        assertEquals(0, TimeUtils.parseDuration("1h later"));
        assertEquals(0, TimeUtils.parseDuration("999999999999999999999d"));
        assertEquals(0, TimeUtils.parseDuration(null));
    }

    @Test
    void formatsDurationsWithoutTrailingWhitespace() {
        assertEquals("1д 2ч 3м 4с", TimeUtils.formatDuration(93_784));
        assertEquals("0с", TimeUtils.formatDuration(0));
    }
}
