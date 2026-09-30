package me.admin.gui.integration;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AltDetector;
import me.admin.gui.manager.MuteManager;
import me.admin.gui.manager.WarnManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final AdvancedModeratorGUI plugin;

    public PlaceholderAPIHook(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "amgui";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AdminGUI";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;

        String[] args = params.split("_", 2);
        String targetPlayer = player.getName();

        if (args.length > 1 && !args[1].isEmpty()) {
            targetPlayer = args[1];
        }

        OfflinePlayer target = resolvePlayer(targetPlayer);
        if (target == null && !args[0].equals("bans_total") && !args[0].equals("staff_online")) {
            return "0";
        }

        return switch (args[0]) {
            case "warns" -> String.valueOf(plugin.getWarnManager().getWarnCount(target.getUniqueId()));
            case "muted" -> String.valueOf(plugin.getMuteManager().isMuted(target.getUniqueId()));
            case "bans_total" -> String.valueOf(me.admin.gui.utils.BanService.activeProfileBanCount());
            case "staff_online" -> String.valueOf(Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("amgui.staffchat")).count());
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "alts" -> {
                if (target == null) yield "0";
                yield String.valueOf(plugin.getAltDetector().findAltUuids(target.getUniqueId()).size());
            }
            default -> null;
        };
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(p.getName())) return p;
        }
        return null;
    }
}
