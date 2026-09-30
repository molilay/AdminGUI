package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class ReportCommand implements CommandExecutor {

    private final AdvancedModeratorGUI plugin;
    private static final List<String> VALID_CATEGORIES = Arrays.asList("hacking", "chat", "griefing", "other");

    public ReportCommand(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (!player.hasPermission("amgui.report.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage("§cИспользование: /report <ник> <категория> <причина>");
            player.sendMessage("§7Категории: hacking, chat, griefing, other");
            return true;
        }

        String targetName = args[0];
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage("§cНельзя жаловаться на самого себя.");
            return true;
        }

        String category = args[1].toLowerCase();
        if (!VALID_CATEGORIES.contains(category)) {
            player.sendMessage("§cНеверная категория. Доступные: hacking, chat, griefing, other");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cИгрок не найден или оффлайн.");
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (reason.length() > 200) {
            player.sendMessage("§cПричина слишком длинная (макс. 200 символов).");
            return true;
        }

        int id = plugin.getReportManager().submit(player.getName(), target.getName(), reason, category);
        if (id < 0) return true;

        player.sendMessage("§a✓ Ваша жалоба на §f" + target.getName() + " §aпринята (ID: " + id + ", категория: " + category + ").");
        player.sendMessage("§7Модераторы рассмотрят её в ближайшее время.");
        return true;
    }
}
