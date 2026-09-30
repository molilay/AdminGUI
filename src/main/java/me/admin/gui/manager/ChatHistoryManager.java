package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.SensitiveCommandFilter;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatHistoryManager implements Listener {

    private final AdvancedModeratorGUI plugin;
    public record Health(int players, long messages, long commands, long sensitiveCommandsDiscarded,
                         int maxPerPlayer) {}

    private final Map<UUID, BoundedConcurrentDeque<ChatMessage>> history = new ConcurrentHashMap<>();
    private volatile int maxMessages;
    private final java.util.concurrent.atomic.LongAdder messages = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder commands = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder sensitiveDiscarded = new java.util.concurrent.atomic.LongAdder();

    public ChatHistoryManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.maxMessages = Math.clamp(plugin.getConfig().getInt("chat-history.max-per-player", 200), 10, 2000);
    }

    public record ChatMessage(String message, long timestamp, boolean isCommand) {
        public String getFormatted() {
            String time = TimeUtils.formatLogTime(timestamp);
            String prefix = isCommand ? "&e/&f" : "&f";
            return "&7[" + time + "] " + prefix + message;
        }
    }

    public List<ChatMessage> getHistory(UUID uuid) {
        BoundedConcurrentDeque<ChatMessage> messages = history.get(uuid);
        return messages == null ? List.of() : messages.snapshot();
    }

    /** Explicit privacy operation; chat history is memory-only and disappears immediately. */
    public void clearHistory(UUID uuid) { if (uuid != null) history.remove(uuid); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;
        record(event.getPlayer().getUniqueId(), net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String msg = event.getMessage();
        String command = SensitiveCommandFilter.commandName(msg);
        if ("sc".equals(command) || "staffchat".equals(command)) return;
        if (SensitiveCommandFilter.isSensitive(msg)) {
            sensitiveDiscarded.increment();
            return;
        }
        record(event.getPlayer().getUniqueId(), msg, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        history.remove(uuid);
    }

    public void record(UUID uuid, String message, boolean isCommand) {
        if (uuid == null || message == null) return;
        history.computeIfAbsent(uuid, ignored -> new BoundedConcurrentDeque<>(maxMessages))
                .addLast(new ChatMessage(message, System.currentTimeMillis(), isCommand));
        if (isCommand) commands.increment(); else messages.increment();
    }

    public void reload() {
        maxMessages = Math.clamp(plugin.getConfig().getInt("chat-history.max-per-player", 200), 10, 2000);
        history.values().forEach(buffer -> buffer.resize(maxMessages));
    }

    public Health health() {
        return new Health(history.size(), messages.sum(), commands.sum(), sensitiveDiscarded.sum(), maxMessages);
    }
}
