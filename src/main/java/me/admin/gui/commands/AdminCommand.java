package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AdvancedModeratorGUI plugin;

    public AdminCommand(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§8[§cAM§8] §7AdvancedModeratorGUI v2.0.0");
            sender.sendMessage("§8/sub-commands: §f/amgui reload, /amgui check");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("amgui.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                plugin.getConfigManager().reload();
                plugin.getHeadCacheManager().clearCache();
                plugin.getDiscordWebhook().ifPresent(w -> w.reload());
                sender.sendMessage("§a✓ Конфигурация перезагружена.");
            }
            case "check" -> {
                sender.sendMessage("§8[§cAM§8] §7Статус:");
                sender.sendMessage(" §7- LuckPerms: " + (plugin.getLuckPermsIntegration() != null ? "§a✓" : "§c✗"));
                sender.sendMessage(" §7- База данных: " + (plugin.getDatabaseManager() != null ? "§a✓" : "§c✗"));
                sender.sendMessage(" §7- Discord Webhook: " + (plugin.getDiscordWebhook().map(d -> d.isEnabled() ? "§a✓" : "§7✗").orElse("§c✗")));
                sender.sendMessage(" §7- Vault: " + (plugin.getVaultIntegration().map(v -> v.isEnabled() ? "§a✓" : "§7✗").orElse("§c✗")));
            }
            default -> {
                sender.sendMessage("§cНеизвестная команда. Используйте /amgui reload");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "check");
        }
        return List.of();
    }
}
