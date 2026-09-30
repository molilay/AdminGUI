package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AutoModManager;
import me.admin.gui.manager.PlayerModerationContext;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.util.UUID;

public class AutoModListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public AutoModListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;
        UUID playerId = event.getPlayer().getUniqueId();
        PlayerModerationContext context = plugin.getPlayerModerationContextCache().get(playerId);
        if (plugin.getAntiRaidManager().shouldBlockChat(context.antiRaidBypass())) {
            event.setCancelled(true);
            plugin.getTaskExecutor().runOnMain(() -> {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online != null) plugin.getAntiRaidManager().notifyChatBlocked(online);
            });
            return;
        }
        if (!plugin.getAutoModManager().isCheckChat()) return;

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.getAutoModManager().shouldIgnore(context)) return;

        if (plugin.getAutoModManager().isBlacklistEnabled() && plugin.getAutoModManager().isBlacklisted(message)) {
            if (!plugin.getAutoModManager().isMonitorMode()) event.setCancelled(true);
            plugin.getTaskExecutor().runOnMain(() -> {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online != null) plugin.getAutoModManager().executeBlacklist(online);
            });
            return;
        }

        AutoModManager.CheckResult result = plugin.getAutoModManager().check(context, message, false);
        if (result != null) {
            if (!plugin.getAutoModManager().isMonitorMode()) event.setCancelled(true);
            plugin.getTaskExecutor().runOnMain(() -> {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online != null) plugin.getAutoModManager().execute(online, result, false);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getAutoModManager().isCheckCommands()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();
        PlayerModerationContext context = plugin.getPlayerModerationContextCache().refresh(player);

        AutoModManager.CheckResult result = plugin.getAutoModManager().check(context, message, true);
        if (result != null) {
            if (!plugin.getAutoModManager().isMonitorMode()) event.setCancelled(true);
            plugin.getAutoModManager().execute(player, result, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getAutoModManager().isCheckSigns()) return;

        Player player = event.getPlayer();
        PlayerModerationContext context = plugin.getPlayerModerationContextCache().refresh(player);
        for (int i = 0; i < event.lines().size(); i++) {
            Component comp = event.line(i);
            if (comp == null) continue;
            String line = PlainTextComponentSerializer.plainText().serialize(comp);
            if (line.isEmpty()) continue;

            AutoModManager.CheckResult result = plugin.getAutoModManager().check(context, line, false);
            if (result != null) {
                if (!plugin.getAutoModManager().isMonitorMode()) event.setCancelled(true);
                plugin.getAutoModManager().execute(player, result, false);
                return;
            }
        }
    }
}
