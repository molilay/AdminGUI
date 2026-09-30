package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPolicyTest {

    @Test
    void expiresOldThenKeepsNewestPerOwnerAndGlobally() {
        long now = 100_000L;
        var deletes = RetentionPolicy.selectDeletes(List.of(
                new RetentionPolicy.Entry("a", "a-old", 1_000L, 1),
                new RetentionPolicy.Entry("a", "a-1", 99_000L, 2),
                new RetentionPolicy.Entry("a", "a-2", 98_000L, 3),
                new RetentionPolicy.Entry("a", "a-3", 97_000L, 4),
                new RetentionPolicy.Entry("b", "b-1", 96_000L, 5),
                new RetentionPolicy.Entry("c", "c-1", 95_000L, 6)
        ), now, 10_000L, 2, 3);

        assertTrue(deletes.contains("a-old"));
        assertTrue(deletes.contains("a-3"));
        assertTrue(deletes.contains("c-1"));
        assertEquals(3, deletes.size());
    }

    @Test
    void selectionIsStableForEqualTimestamps() {
        var deletes = RetentionPolicy.selectDeletes(List.of(
                new RetentionPolicy.Entry("a", "b", 10L, 1),
                new RetentionPolicy.Entry("a", "a", 10L, 2)
        ), 10L, 0L, 1, 10);
        assertEquals(java.util.Set.of("b"), deletes);
    }
}
