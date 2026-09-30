package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread-owned producer of immutable moderation contexts. */
public final class PlayerModerationContextCache implements Listener, AutoCloseable {

    public record Health(int entries, long refreshes, long misses, boolean running) {}

    private final AdvancedModeratorGUI plugin;
    private final Map<UUID, PlayerModerationContext> contexts = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.LongAdder refreshes = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder misses = new java.util.concurrent.atomic.LongAdder();
    private volatile BukkitTask refreshTask;

    public PlayerModerationContextCache(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (refreshTask != null) return;
        Bukkit.getOnlinePlayers().forEach(this::refresh);
        long ticks = Math.clamp(plugin.getConfig().getLong("runtime.player-context-refresh-ticks", 40L), 10L, 1200L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::refresh), ticks, ticks);
    }

    public void reload() {
        synchronized (this) {
            if (refreshTask != null) {
                refreshTask.cancel();
                refreshTask = null;
                start();
            }
        }
    }

    /** Must only be invoked from the Bukkit main thread. */
    public PlayerModerationContext refresh(Player player) {
        String group;
        try {
            group = plugin.getLuckPermsIntegration().getLoadedPrimaryGroup(player.getUniqueId());
        } catch (RuntimeException unavailable) {
            group = "default";
        }
        PlayerModerationContext context = new PlayerModerationContext(
                player.getUniqueId(), player.getName(), player.getWorld().getName(), group,
                player.hasPermission(plugin.getAutoModManager().getBypassPermission()),
                player.hasPermission("amgui.antiraid.bypass"), System.currentTimeMillis());
        contexts.put(context.uuid(), context);
        refreshes.increment();
        return context;
    }

    /** Async-safe; returns a fail-closed context when an event raced a join. */
    public PlayerModerationContext get(UUID uuid) {
        PlayerModerationContext value = contexts.get(uuid);
        if (value != null) return value;
        misses.increment();
        return PlayerModerationContext.restrictive(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { refresh(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) { refresh(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { contexts.remove(event.getPlayer().getUniqueId()); }

    public Health health() { return new Health(contexts.size(), refreshes.sum(), misses.sum(), refreshTask != null); }

    @Override
    public synchronized void close() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        contexts.clear();
    }
}
