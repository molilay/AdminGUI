package me.admin.gui.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class HeadCacheManager {

    private record Cached<T>(T value, long expiresAt) {}

    private final me.admin.gui.AdvancedModeratorGUI plugin;
    private volatile int maxEntries;
    private volatile long ttlMillis;
    private final Map<UUID, Cached<ItemStack>> headCache;
    private final Map<UUID, Cached<PlayerProfile>> profileCache;
    private final Map<UUID, CompletableFuture<ItemStack>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong lastProfileFetch = new AtomicLong(0);
    private static final long MIN_FETCH_INTERVAL_MS = 200;

    public HeadCacheManager(me.admin.gui.AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        reloadSettings();
        this.headCache = lru(() -> maxEntries);
        this.profileCache = lru(() -> maxEntries);
    }

    private static <T> Map<UUID, Cached<T>> lru(java.util.function.IntSupplier maximum) {
        return java.util.Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID, Cached<T>> eldest) {
                return size() > maximum.getAsInt();
            }
        });
    }

    public ItemStack getHead(OfflinePlayer player) {
        Cached<ItemStack> cached = live(headCache, player.getUniqueId());
        if (cached != null) {
            return cached.value().clone();
        }

        ItemStack head = createHead(player);
        headCache.put(player.getUniqueId(), new Cached<>(head.clone(), expiresAt()));
        return head;
    }

    public CompletableFuture<ItemStack> getHeadAsync(OfflinePlayer player) {
        Cached<ItemStack> cached = live(headCache, player.getUniqueId());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.value().clone());
        }
        if (player.isOnline()) return CompletableFuture.completedFuture(getHead(player));
        return inFlight.computeIfAbsent(player.getUniqueId(), ignored -> loadOfflineHead(player)
                .whenComplete((result, failure) -> inFlight.remove(player.getUniqueId())));
    }

    private CompletableFuture<ItemStack> loadOfflineHead(OfflinePlayer player) {
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        plugin.getTaskExecutor().supply("head-profile:" + playerId, () -> {
                    PlayerProfile profile = Bukkit.createProfile(playerId, playerName);
                    if (playerName != null) profile.complete();
                    return profile;
                })
                .whenComplete((profile, error) -> {
                    boolean dispatched = plugin.getTaskExecutor().runOnMain(() -> {
                        OfflinePlayer current = Bukkit.getOfflinePlayer(playerId);
                        if (error == null && profile != null) {
                    if (!profile.getProperties().isEmpty()) {
                                profileCache.put(playerId, new Cached<>(profile, expiresAt()));
                    }
                            ItemStack head = createHead(current);
                            headCache.put(playerId, new Cached<>(head.clone(), expiresAt()));
                    future.complete(head);
                        } else {
                            future.complete(getHead(current));
                        }
                    });
                    if (!dispatched) future.completeExceptionally(
                            new java.util.concurrent.CancellationException("plugin disabled"));
                });
        return future;
    }

    private ItemStack createHead(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta meta)) return head;
        String name = player.getName();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§e" + (name != null ? name : player.getUniqueId().toString().substring(0, 8))));
        if (player.isOnline()) {
            PlayerProfile profile = ((Player) player).getPlayerProfile();
            meta.setPlayerProfile(profile);
            profileCache.put(player.getUniqueId(), new Cached<>(profile, expiresAt()));
        } else {
            Cached<PlayerProfile> cachedProfile = live(profileCache, player.getUniqueId());
            if (cachedProfile != null) {
                meta.setPlayerProfile(cachedProfile.value());
            }
        }
        head.setItemMeta(meta);
        return head;
    }

    public void preCache(OfflinePlayer player) {
        Cached<ItemStack> cached = live(headCache, player.getUniqueId());
        if (cached != null) return;
        if (live(profileCache, player.getUniqueId()) != null) return;

        if (player.isOnline()) {
            PlayerProfile profile = ((Player) player).getPlayerProfile();
            profileCache.put(player.getUniqueId(), new Cached<>(profile, expiresAt()));
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastProfileFetch.get();
        if (now - last < MIN_FETCH_INTERVAL_MS) return;
        if (!lastProfileFetch.compareAndSet(last, now)) return;

        getHeadAsync(player);
    }

    public void clearCache() {
        inFlight.values().forEach(future -> future.completeExceptionally(
                new java.util.concurrent.CancellationException("head cache cleared")));
        headCache.clear();
        profileCache.clear();
        inFlight.clear();
    }

    public int cachedHeads() { return headCache.size(); }
    public int cachedProfiles() { return profileCache.size(); }
    public int pendingRequests() { return inFlight.size(); }

    public void reload() {
        reloadSettings();
        trim(headCache);
        trim(profileCache);
    }

    private void reloadSettings() {
        maxEntries = Math.clamp(plugin.getConfig().getInt("cache.heads.max-entries", 1000), 50, 10_000);
        ttlMillis = Math.clamp(plugin.getConfig().getLong("cache.heads.ttl-seconds", 3600), 60, 86_400) * 1000L;
    }

    private <T> void trim(Map<UUID, Cached<T>> cache) {
        synchronized (cache) {
            var iterator = cache.entrySet().iterator();
            while (cache.size() > maxEntries && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private long expiresAt() { return System.currentTimeMillis() + ttlMillis; }

    private static <T> Cached<T> live(Map<UUID, Cached<T>> cache, UUID key) {
        Cached<T> value = cache.get(key);
        if (value != null && value.expiresAt() <= System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return value;
    }
}
