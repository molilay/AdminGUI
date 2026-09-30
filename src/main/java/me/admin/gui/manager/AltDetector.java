package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AltDetector {

    public record RelatedAccount(UUID uuid, String name, Set<String> sharedIps, String lastIp, long lastSeen) {}
    private static final Comparator<RelatedAccount> RELATED_ORDER = Comparator
            .comparingInt((RelatedAccount value) -> value.sharedIps().size()).reversed()
            .thenComparing(Comparator.comparingLong(RelatedAccount::lastSeen).reversed());

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final Object stateLock = new Object();
    private final Object fileWriteLock = new Object();
    private final YamlConfiguration data;
    private final ArtifactSignatureService identitySigner;
    private final long rawIpRetentionMillis;
    private final int maxIdentitiesPerPlayer;
    private final int maxRawHistoryPerPlayer;
    private final Map<String, Set<UUID>> ipIndex = new HashMap<>();
    private record PendingWrite(long version, String snapshot) {}
    private final AtomicReference<PendingWrite> pendingSnapshot = new AtomicReference<>();
    private final AtomicBoolean writerRunning = new AtomicBoolean();
    private final AtomicLong writeVersion = new AtomicLong();
    private long writtenVersion;

    public AltDetector(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("Failed to create playerdata.yml: " + e.getMessage()); }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        this.identitySigner = new ArtifactSignatureService(plugin);
        this.rawIpRetentionMillis = Math.clamp(plugin.getConfig().getLong("privacy.raw-ip-retention-days", 30), 1, 365)
                * 86_400_000L;
        this.maxIdentitiesPerPlayer = Math.clamp(plugin.getConfig().getInt("privacy.max-ip-identities-per-player", 50), 5, 500);
        this.maxRawHistoryPerPlayer = Math.clamp(plugin.getConfig().getInt("privacy.max-raw-ip-history-per-player", 20), 1, 100);
        boolean migrated = migrateAndPrune();
        rebuildIndex();
        if (migrated) queueSave(data.saveToString());
    }

    public List<String> findAlts(String ip, UUID excludeId) {
        if (ip == null || ip.isBlank()) return List.of();
        synchronized (stateLock) {
            List<String> alts = new ArrayList<>();
            for (UUID uuid : ipIndex.getOrDefault(identity(ip), Set.of())) {
                if (!uuid.equals(excludeId)) alts.add(data.getString(uuid + ".name", "?"));
            }
            alts.sort(String.CASE_INSENSITIVE_ORDER);
            return List.copyOf(alts);
        }
    }

    public List<String> findAlts(UUID playerId) {
        return findRelatedAccounts(playerId).stream().map(RelatedAccount::name)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public Map<String, UUID> findAltUuids(UUID playerId) {
        Map<String, UUID> result = new LinkedHashMap<>();
        findRelatedAccounts(playerId).stream()
                .sorted(Comparator.comparing(RelatedAccount::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(account -> result.put(account.name(), account.uuid()));
        return result;
    }

    /** Finds accounts sharing any historical IP, not only the latest one. */
    public List<RelatedAccount> findRelatedAccounts(UUID playerId) {
        synchronized (stateLock) {
            Set<String> targetIps = readPlayerIdentities(playerId);
            if (targetIps.isEmpty()) return List.of();
            Set<UUID> candidates = new LinkedHashSet<>();
            targetIps.forEach(ip -> candidates.addAll(ipIndex.getOrDefault(ip, Set.of())));
            candidates.remove(playerId);
            List<RelatedAccount> result = new ArrayList<>();
            for (UUID uuid : candidates) {
                Set<String> shared = new LinkedHashSet<>(readPlayerIdentities(uuid));
                shared.retainAll(targetIps);
                if (!shared.isEmpty()) {
                    result.add(new RelatedAccount(uuid, data.getString(uuid + ".name", "?"), Set.copyOf(shared),
                            liveLastIp(uuid), data.getLong(uuid + ".last-seen", 0L)));
                }
            }
            result.sort(RELATED_ORDER);
            return List.copyOf(result);
        }
    }

    static Comparator<RelatedAccount> relatedOrder() { return RELATED_ORDER; }

    private Set<String> readPlayerIdentities(UUID uuid) {
        Set<String> result = new LinkedHashSet<>();
        String base = uuid.toString();
        for (Map<String, Object> entry : readMaps(data.getList(base + ".ip-identities"))) {
            Object value = entry.get("id");
            if (value instanceof String id && !id.isBlank()) result.add(id);
        }
        String lastIp = liveLastIp(uuid);
        if (!lastIp.isBlank()) result.add(identity(lastIp));
        for (Map<String, Object> entry : readMaps(data.getList(base + ".ip-history"))) {
            Object value = entry.get("ip");
            if (value instanceof String ip && !ip.isBlank()) result.add(identity(ip));
        }
        return result;
    }

    public void recordLogin(UUID uuid, String name, String ip) {
        if (ip == null || ip.isBlank()) return;
        synchronized (stateLock) {
            long now = System.currentTimeMillis();
            data.set(uuid + ".name", name);
            data.set(uuid + ".last-ip", ip);
            data.set(uuid + ".last-ip-seen", now);
            data.set(uuid + ".last-seen", now);

            List<Map<String, Object>> history = readMaps(data.getList(uuid + ".ip-history"));
            boolean found = false;
            for (Map<String, Object> entry : history) {
                if (ip.equals(entry.get("ip"))) {
                    entry.put("last", now);
                    found = true;
                    break;
                }
            }
            if (!found) {
                Map<String, Object> newEntry = new LinkedHashMap<>();
                newEntry.put("ip", ip);
                newEntry.put("first", now);
                newEntry.put("last", now);
                history.add(newEntry);
            }
            data.set(uuid + ".ip-history", history);
            upsertIdentity(uuid, identity(ip), now, now);
            ipIndex.computeIfAbsent(identity(ip), ignored -> new LinkedHashSet<>()).add(uuid);
            prunePlayer(uuid, now - rawIpRetentionMillis);
            queueSave(data.saveToString());
        }
    }

    public String getLastIp(UUID uuid) {
        synchronized (stateLock) {
            return liveLastIp(uuid);
        }
    }

    public Map<String, long[]> getIpData(UUID uuid) {
        synchronized (stateLock) {
            Map<String, long[]> result = new LinkedHashMap<>();
            List<?> rawHistory = data.getList(uuid + ".ip-history");
            List<Map<String, Object>> history = readMaps(rawHistory);
            if (rawHistory == null) {
                String ip = liveLastIp(uuid);
                long last = data.getLong(uuid + ".last-seen", 0);
                if (!ip.isEmpty()) result.put(ip, new long[]{last, last});
                return result;
            }
            for (Map<String, Object> entry : history) {
                Object ipValue = entry.get("ip");
                String ip = ipValue instanceof String text ? text : null;
                long first = number(entry.get("first"));
                long last = number(entry.get("last"));
                if (ip != null && last >= System.currentTimeMillis() - rawIpRetentionMillis)
                    result.put(ip, new long[]{first, last});
            }
            return result;
        }
    }

    public int indexedPlayers() {
        synchronized (stateLock) { return data.getKeys(false).size(); }
    }

    public int indexedIps() {
        synchronized (stateLock) { return ipIndex.size(); }
    }

    public void flush() {
        String snapshot;
        synchronized (stateLock) { snapshot = data.saveToString(); }
        pendingSnapshot.set(null);
        writeSnapshot(new PendingWrite(writeVersion.incrementAndGet(), snapshot));
    }

    private void rebuildIndex() {
        synchronized (stateLock) {
            ipIndex.clear();
            for (String key : data.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    for (String identity : readPlayerIdentities(uuid)) {
                        ipIndex.computeIfAbsent(identity, ignored -> new LinkedHashSet<>()).add(uuid);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Preserve usable records if one top-level key is corrupt.
                }
            }
        }
    }

    /** Removes expired raw IP values while retaining bounded HMAC identities for alt matching. */
    public int purgeExpiredRawIps() {
        synchronized (stateLock) {
            long cutoff = System.currentTimeMillis() - rawIpRetentionMillis;
            int changed = 0;
            for (String key : data.getKeys(false)) {
                try {
                    if (prunePlayer(UUID.fromString(key), cutoff)) changed++;
                } catch (IllegalArgumentException ignored) { }
            }
            if (changed > 0) queueSave(data.saveToString());
            return changed;
        }
    }

    /** Privacy purge for one profile. Long-term HMAC identities and raw values are both removed. */
    public boolean purgePlayer(UUID uuid) {
        synchronized (stateLock) {
            String path = uuid.toString();
            if (!data.contains(path)) return false;
            data.set(path + ".last-ip", null);
            data.set(path + ".last-ip-seen", null);
            data.set(path + ".ip-history", null);
            data.set(path + ".ip-identities", null);
            rebuildIndex();
            queueSave(data.saveToString());
            return true;
        }
    }

    private boolean migrateAndPrune() {
        synchronized (stateLock) {
            boolean changed = false;
            long cutoff = System.currentTimeMillis() - rawIpRetentionMillis;
            for (String key : data.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<Map<String, Object>> history = readMaps(data.getList(key + ".ip-history"));
                    for (Map<String, Object> entry : history) {
                        Object value = entry.get("ip");
                        if (value instanceof String ip && !ip.isBlank()) {
                            upsertIdentity(uuid, identity(ip), number(entry.get("first")), number(entry.get("last")));
                            changed = true;
                        }
                    }
                    String lastIp = data.getString(key + ".last-ip", "");
                    if (!lastIp.isBlank()) {
                        long seen = data.getLong(key + ".last-ip-seen", data.getLong(key + ".last-seen", 0L));
                        upsertIdentity(uuid, identity(lastIp), seen, seen);
                        data.set(key + ".last-ip-seen", seen);
                    }
                    changed |= prunePlayer(uuid, cutoff);
                } catch (IllegalArgumentException ignored) { }
            }
            return changed;
        }
    }

    private boolean prunePlayer(UUID uuid, long cutoff) {
        String base = uuid.toString();
        boolean changed = false;
        List<Map<String, Object>> history = readMaps(data.getList(base + ".ip-history"));
        int before = history.size();
        history.removeIf(entry -> number(entry.get("last")) < cutoff);
        history.sort(Comparator.comparingLong((Map<String, Object> entry) -> number(entry.get("last"))).reversed());
        if (history.size() > maxRawHistoryPerPlayer) history = new ArrayList<>(history.subList(0, maxRawHistoryPerPlayer));
        if (history.size() != before) {
            data.set(base + ".ip-history", history.isEmpty() ? null : history);
            changed = true;
        }
        long lastSeen = data.getLong(base + ".last-ip-seen", data.getLong(base + ".last-seen", 0L));
        if ((lastSeen <= 0L || lastSeen < cutoff) && data.contains(base + ".last-ip")) {
            data.set(base + ".last-ip", null);
            data.set(base + ".last-ip-seen", null);
            changed = true;
        }
        List<Map<String, Object>> identities = readMaps(data.getList(base + ".ip-identities"));
        identities.sort(Comparator.comparingLong((Map<String, Object> entry) -> number(entry.get("last"))).reversed());
        if (identities.size() > maxIdentitiesPerPlayer) {
            identities = new ArrayList<>(identities.subList(0, maxIdentitiesPerPlayer));
            changed = true;
        }
        data.set(base + ".ip-identities", identities.isEmpty() ? null : identities);
        return changed;
    }

    private void upsertIdentity(UUID uuid, String identity, long first, long last) {
        String path = uuid + ".ip-identities";
        List<Map<String, Object>> identities = readMaps(data.getList(path));
        Map<String, Object> found = null;
        for (Map<String, Object> entry : identities) {
            if (identity.equals(entry.get("id"))) { found = entry; break; }
        }
        if (found == null) {
            found = new LinkedHashMap<>();
            found.put("id", identity);
            found.put("first", first);
            identities.add(found);
        } else if (first > 0L && (number(found.get("first")) <= 0L || first < number(found.get("first")))) {
            found.put("first", first);
        }
        found.put("last", Math.max(last, number(found.get("last"))));
        data.set(path, identities);
    }

    private String identity(String ip) {
        return "hmac:" + identitySigner.keyId() + ":" + identitySigner.sign(
                ("IP-ID-V1|" + ip.trim().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    private String liveLastIp(UUID uuid) {
        String base = uuid.toString();
        long seen = data.getLong(base + ".last-ip-seen", data.getLong(base + ".last-seen", 0L));
        if (seen <= 0L || seen < System.currentTimeMillis() - rawIpRetentionMillis) return "";
        return data.getString(base + ".last-ip", "");
    }

    private void queueSave(String snapshot) {
        pendingSnapshot.set(new PendingWrite(writeVersion.incrementAndGet(), snapshot));
        if (!writerRunning.compareAndSet(false, true)) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drainWrites);
    }

    private void drainWrites() {
        try {
            PendingWrite snapshot;
            while ((snapshot = pendingSnapshot.getAndSet(null)) != null) writeSnapshot(snapshot);
        } finally {
            writerRunning.set(false);
            if (pendingSnapshot.get() != null && writerRunning.compareAndSet(false, true)) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drainWrites);
            }
        }
    }

    private void writeSnapshot(PendingWrite pending) {
        synchronized (fileWriteLock) {
            if (pending.version() <= writtenVersion) return;
            try {
                File parent = dataFile.getParentFile();
                if (parent != null) Files.createDirectories(parent.toPath());
                File temp = new File(parent, dataFile.getName() + ".tmp");
                Files.writeString(temp.toPath(), pending.snapshot(), StandardCharsets.UTF_8);
                try {
                    Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                writtenVersion = pending.version();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save playerdata atomically: " + e.getMessage());
            }
        }
    }

    private static List<Map<String, Object>> readMaps(List<?> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (values == null) return result;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> source)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, item) -> copy.put(String.valueOf(key), item));
            result.add(copy);
        }
        return result;
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
