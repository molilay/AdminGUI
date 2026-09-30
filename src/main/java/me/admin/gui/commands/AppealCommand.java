package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AppealCommand implements CommandExecutor {

    private final AdvancedModeratorGUI plugin;

    public AppealCommand(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /appeal <ник_игрока> <причина>");
            sender.sendMessage("§7Подать апелляцию за игрока (разбан).");
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        boolean isBanned = me.admin.gui.utils.BanService.isProfileBanned(targetName);
        if (!isBanned) {
            sender.sendMessage("§cИгрок §f" + targetName + " §cне забанен.");
            return true;
        }

        String submitter = sender instanceof Player ? sender.getName() : "Console";
        int id = plugin.getAppealManager().submit(targetName, submitter, reason);
        sender.sendMessage("§a✓ Апелляция за §f" + targetName + " §aпринята (ID: " + id + ").");
        sender.sendMessage("§7Модераторы рассмотрят её.");
        return true;
    }
}
