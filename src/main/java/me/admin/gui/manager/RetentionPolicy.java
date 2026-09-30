package me.admin.gui.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure deterministic retention selector shared by snapshot/cache persistence. */
public final class RetentionPolicy {

    public record Entry(String owner, String id, long timestamp, long generation) { }

    private RetentionPolicy() { }

    public static Set<String> selectDeletes(List<Entry> source, long now, long ttlMillis,
                                            int maxPerOwner, int maxTotal) {
        List<Entry> entries = new ArrayList<>(source == null ? List.of() : source);
        entries.sort(Comparator.comparingLong(Entry::timestamp).reversed()
                .thenComparing(Entry::id));
        Set<String> deletes = new HashSet<>();
        long cutoff = ttlMillis <= 0 ? Long.MIN_VALUE : now - ttlMillis;
        for (Entry entry : entries) {
            if (entry.timestamp() <= 0 || entry.timestamp() < cutoff) deletes.add(entry.id());
        }

        Map<String, Integer> keptPerOwner = new HashMap<>();
        int keptTotal = 0;
        int safePerOwner = Math.max(1, maxPerOwner);
        int safeTotal = Math.max(1, maxTotal);
        for (Entry entry : entries) {
            if (deletes.contains(entry.id())) continue;
            int ownerCount = keptPerOwner.getOrDefault(entry.owner(), 0);
            if (ownerCount >= safePerOwner || keptTotal >= safeTotal) {
                deletes.add(entry.id());
                continue;
            }
            keptPerOwner.put(entry.owner(), ownerCount + 1);
            keptTotal++;
        }
        return Set.copyOf(deletes);
    }
}
