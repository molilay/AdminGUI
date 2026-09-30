package me.admin.gui.compat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for {@link ServerCompat}: version parsing, comparison and the candidate resolver.
 * Nothing here touches Bukkit, so the tests run without a server.
 */
class ServerCompatTest {

    @Test
    void parsesPaperSpigotAndPlainVersionStrings() {
        assertEquals(new ServerCompat.Version(1, 21, 4), ServerCompat.Version.parse("1.21.4"));
        assertEquals(new ServerCompat.Version(1, 21, 4), ServerCompat.Version.parse("1.21.4-R0.1-SNAPSHOT"));
        assertEquals(new ServerCompat.Version(1, 21, 0), ServerCompat.Version.parse("1.21"));
        assertEquals(new ServerCompat.Version(1, 21, 4), ServerCompat.Version.parse("git-Paper-196 (MC: 1.21.4)"));
        assertEquals(new ServerCompat.Version(1, 26, 1), ServerCompat.Version.parse("1.26.1"));
    }

    @Test
    void rejectsUnparsableInput() {
        assertNull(ServerCompat.Version.parse(null));
        assertNull(ServerCompat.Version.parse(""));
        assertNull(ServerCompat.Version.parse("   "));
        assertNull(ServerCompat.Version.parse("unknown"));
    }

    @Test
    void comparesVersionsNumerically() {
        ServerCompat.Version minimum = new ServerCompat.Version(ServerCompat.MINIMUM_MAJOR,
                ServerCompat.MINIMUM_MINOR, ServerCompat.MINIMUM_PATCH);
        assertEquals("1.21.4", minimum.toString());
        assertTrue(minimum.atLeast(1, 21, 4));
        assertTrue(new ServerCompat.Version(1, 22, 0).atLeast(1, 21, 4));
        assertTrue(new ServerCompat.Version(1, 21, 5).atLeast(1, 21, 4));
        assertFalse(new ServerCompat.Version(1, 21, 3).atLeast(1, 21, 4));
        assertFalse(new ServerCompat.Version(1, 20, 6).atLeast(1, 21, 4));
        // Lexicographic comparison would be wrong: 1.9 < 1.21 numerically.
        assertTrue(new ServerCompat.Version(1, 21, 4).compareTo(new ServerCompat.Version(1, 9, 0)) > 0);
    }

    @Test
    void unknownVersionIsNeverSupported() {
        assertFalse(ServerCompat.Version.UNKNOWN.known());
        assertFalse(ServerCompat.Version.UNKNOWN.atLeast(1, 21, 4));
        assertTrue(new ServerCompat.Version(1, 21, 4).known());
    }

    @Test
    void resolverReturnsFirstAvailableCandidate() {
        assertEquals(Optional.of("a"), ServerCompat.firstPresent(new String[]{"a", "b"},
                value -> "a".equals(value) ? Optional.of(value) : Optional.empty()));
        assertEquals(Optional.of("b"), ServerCompat.firstPresent(new String[]{"missing", "b"},
                value -> "b".equals(value) ? Optional.of(value) : Optional.empty()));
    }

    @Test
    void resolverSkipsNullBlankAndThrowingCandidates() {
        assertEquals(Optional.empty(), ServerCompat.firstPresent(new String[]{"missing"}, value -> Optional.empty()));
        assertEquals(Optional.empty(), ServerCompat.firstPresent(new String[]{null, "", "  "}, value -> Optional.of(value)));
        assertEquals(Optional.empty(), ServerCompat.firstPresent((String[]) null, value -> Optional.of(value)));
        assertEquals(Optional.empty(), ServerCompat.firstPresent(new String[]{"boom"}, value -> {
            throw new IllegalStateException("resolver failure");
        }));
    }
}
