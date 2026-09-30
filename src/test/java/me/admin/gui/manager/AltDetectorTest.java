package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AltDetectorTest {

    @Test
    void relatedAccountsSortBySharedIpCountThenNewest() {
        AltDetector.RelatedAccount oneOld = account("old", Set.of("1"), 10);
        AltDetector.RelatedAccount oneNew = account("new", Set.of("2"), 20);
        AltDetector.RelatedAccount two = account("strong", Set.of("1", "2"), 5);
        List<AltDetector.RelatedAccount> values = new ArrayList<>(List.of(oneOld, oneNew, two));

        values.sort(AltDetector.relatedOrder());

        assertEquals(List.of("strong", "new", "old"), values.stream().map(AltDetector.RelatedAccount::name).toList());
    }

    private static AltDetector.RelatedAccount account(String name, Set<String> ips, long lastSeen) {
        return new AltDetector.RelatedAccount(UUID.randomUUID(), name, ips, "", lastSeen);
    }
}
