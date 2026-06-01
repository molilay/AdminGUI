package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffChatCommand implements CommandExecutor {

    private final AdvancedModeratorGUI plugin;

    public StaffChatCommand(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько для игроков.");
            return true;
        }

        if (!player.hasPermission("amgui.staffchat")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            plugin.getStaffChatManager().toggle(player);
            return true;
        }

        String message = String.join(" ", args);
        plugin.getStaffChatManager().sendStaffMessage(player.getName(), message);
        return true;
    }
}
