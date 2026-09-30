package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.gui.GroupListGUI;
import me.admin.gui.gui.ModeratorDashboardGUI;
import me.admin.gui.gui.PlayerCardGUI;
import me.admin.gui.gui.PlayerListGUI;
import me.admin.gui.utils.TimeUtils;
import me.admin.gui.utils.CapabilityRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import me.admin.gui.manager.InvestigationManager;

public class ModCommand implements CommandExecutor, TabCompleter {

    private final AdvancedModeratorGUI plugin;

    public ModCommand(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (!player.hasPermission("amgui.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            new ModeratorDashboardGUI(plugin, player).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "player" -> {
                if (!player.hasPermission("amgui.player")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /mod player <ник>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                        if (p.getName() != null && p.getName().equalsIgnoreCase(args[1])) {
                            target = p;
                            break;
                        }
                    }
                }
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                    return true;
                }
                new PlayerCardGUI(plugin, player, target).open();
            }
            case "groups" -> {
                if (!player.hasPermission("amgui.groups")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                new GroupListGUI(plugin, player).open();
            }
            case "online" -> {
                new PlayerListGUI(plugin, player, true).open();
            }
            case "history" -> {
                if (!player.hasPermission("amgui.logs")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /mod history <ник>");
                    return true;
                }
                String targetName = args[1];
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<LogEntry> logs = plugin.getDatabaseManager().getLogsByTarget(targetName);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (logs.isEmpty()) {
                            player.sendMessage("§eЛогов для " + targetName + " не найдено.");
                            return;
                        }
                        int limit = Math.min(logs.size(), 10);
                        player.sendMessage("§8[§cAM§8] §7Последние " + limit + " логов для §f" + targetName + ":");
                        for (int i = 0; i < limit; i++) {
                            LogEntry e = logs.get(i);
                            player.sendMessage(" §8- §c" + e.getType().toUpperCase() + " §7| §f" + e.getReason()
                                    + " §8(§7" + e.getModerator() + "§8) §8" + TimeUtils.formatLogTime(e.getDate()));
                        }
                        if (logs.size() > limit) {
                            player.sendMessage(" §8... и ещё " + (logs.size() - limit) + " записей");
                        }
                    });
                });
            }
            case "investigate" -> handleInvestigate(player, args);
            case "alert" -> handleAlert(player, args);
            case "inbox" -> {
                if (!player.hasPermission("amgui.inbox.view")) player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                else new me.admin.gui.gui.TriageInboxGUI(plugin, player).open();
            }
            case "simulator" -> {
                if (!player.hasPermission("amgui.simulator")) player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                else new me.admin.gui.gui.SecuritySimulatorGUI(plugin, player).open();
            }
            case "incident", "snapshot" -> openIncident(player, args, args[0].equalsIgnoreCase("snapshot"));
            default -> {
                player.sendMessage(plugin.getLocalizationManager().format("messages.command-help", "&7Доступно: &f/mod %commands%",
                        "commands", String.join(" &8| &f", CapabilityRegistry.visibleModCommands(player))));
            }
        }

        return true;
    }

    private void handleInvestigate(Player player, String[] args) {
        if (!player.hasPermission("amgui.investigate")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        InvestigationManager im = plugin.getInvestigationManager();
        if (args.length < 2) {
            im.toggleInvestigator(player);
            return;
        }
        im.watch(player, args[1]);
    }

    private void openIncident(Player viewer, String[] args, boolean capture) {
        String permission = capture ? "amgui.incident.snapshot" : "amgui.incident.view";
        if (!viewer.hasPermission(permission)) { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
        if (args.length < 2) { viewer.sendMessage("§cИспользование: /mod " + (capture ? "snapshot" : "incident") + " <игрок>"); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) { viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found")); return; }
        if (!capture) { new me.admin.gui.gui.IncidentTimelineGUI(plugin, viewer, target).open(); return; }
        new me.admin.gui.gui.ConfirmGUI(plugin, viewer, "§aСоздать Incident Snapshot для " + target.getName() + "?", () -> {
            if (!viewer.hasPermission("amgui.incident.snapshot")) { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
            var result = plugin.getIncidentManager().capture(target, viewer.getName());
            viewer.sendMessage("§a✓ Incident Snapshot: §f" + result.file().getName());
        }).open();
    }

    private void handleAlert(Player player, String[] args) {
        if (!player.hasPermission("amgui.alert")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cИспользование: /mod alert <ник> <причина>");
            return;
        }
        String targetName = args[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        var target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return;
        }
        target.sendMessage("§8[§cAM§8] §c⚠ Предупреждение от модератора §f" + player.getName() + "§c:");
        target.sendMessage(" §f" + reason);
        player.sendMessage("§a✓ Предупреждение отправлено §f" + targetName);
        String finalTargetName = targetName;
        String finalReason = reason;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().logPunishment("alert", player.getName(), finalTargetName, finalReason, -1);
        });
        plugin.getDiscordWebhook().ifPresent(w -> w.send("alert", finalTargetName, player.getName(), finalReason, "—"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            return CapabilityRegistry.visibleModCommands(player).stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("investigate")
                || args[0].equalsIgnoreCase("alert") || args[0].equalsIgnoreCase("incident") || args[0].equalsIgnoreCase("snapshot"))) {
            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toCollection(ArrayList::new));
            int maxOffline = 50 - suggestions.size();
            if (maxOffline > 0) {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (suggestions.size() >= 50) break;
                    String name = op.getName();
                    if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase()) && !suggestions.contains(name)) {
                        suggestions.add(name);
                    }
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
