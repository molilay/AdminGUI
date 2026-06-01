package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.gui.GroupListGUI;
import me.admin.gui.gui.MainMenu;
import me.admin.gui.gui.PlayerCardGUI;
import me.admin.gui.gui.PlayerListGUI;
import me.admin.gui.utils.TimeUtils;
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

public class ModCommand implements CommandExecutor, TabCompleter {

    private final AdvancedModeratorGUI plugin;

    private static final List<String> SUBCOMMANDS = List.of("player", "groups", "online", "history");

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
            new MainMenu(plugin, player).open();
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
                List<LogEntry> logs = plugin.getDatabaseManager().getLogsByTarget(args[1]);
                if (logs.isEmpty()) {
                    player.sendMessage("§eЛогов для " + args[1] + " не найдено.");
                    return true;
                }
                int limit = Math.min(logs.size(), 10);
                player.sendMessage("§8[§cAM§8] §7Последние " + limit + " логов для §f" + args[1] + ":");
                for (int i = 0; i < limit; i++) {
                    LogEntry e = logs.get(i);
                    player.sendMessage(" §8- §c" + e.getType().toUpperCase() + " §7| §f" + e.getReason()
                            + " §8(§7" + e.getModerator() + "§8) §8" + TimeUtils.formatLogTime(e.getDate()));
                }
                if (logs.size() > limit) {
                    player.sendMessage(" §8... и ещё " + (logs.size() - limit) + " записей");
                }
            }
            default -> {
                player.sendMessage("§cИспользование: /mod [player <ник>|groups|online|history <ник>]");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("history"))) {
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
