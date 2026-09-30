package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.InvestigationManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.UUID;

public class InvestigationListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public InvestigationListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("investigation.command-spy", true)) return;
        Player player = event.getPlayer();
        String command = me.admin.gui.utils.SensitiveCommandFilter.isSensitive(event.getMessage())
                ? me.admin.gui.utils.SensitiveCommandFilter.redact(event.getMessage())
                : event.getMessage();

        InvestigationManager im = plugin.getInvestigationManager();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.equals(player) && im.isInvestigating(staff)) {
                Set<UUID> watched = im.getWatchedPlayers(staff);
                if (watched.contains(player.getUniqueId())) {
                    staff.sendMessage("§8[§6Spy§8] §f" + player.getName() + "§7: " + command);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("investigation.chest-log", true)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryType type = event.getInventory().getType();
        if (!isContainer(type)) return;

        InvestigationManager im = plugin.getInvestigationManager();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.equals(player) && im.isInvestigating(staff)) {
                Set<UUID> watched = im.getWatchedPlayers(staff);
                if (watched.contains(player.getUniqueId())) {
                    String containerName = getContainerName(event);
                    String loc = "§f" + player.getLocation().getBlockX() + " "
                            + player.getLocation().getBlockY() + " "
                            + player.getLocation().getBlockZ();
                    staff.sendMessage("§8[§6Chest§8] §f" + player.getName()
                            + " §eоткрыл §f" + containerName
                            + " §8(§7" + loc + "§8)");
                }
            }
        }
    }

    private boolean isContainer(InventoryType type) {
        return switch (type) {
            case CHEST, BARREL, SHULKER_BOX, DISPENSER, DROPPER,
                 HOPPER, FURNACE, BLAST_FURNACE, SMOKER, BREWING, BEACON,
                 ANVIL, GRINDSTONE, STONECUTTER, LOOM, CARTOGRAPHY,
                 SMITHING, ENCHANTING -> true;
            default -> false;
        };
    }

    private String getContainerName(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        if (event.getInventory().getLocation() != null) {
            Block block = event.getInventory().getLocation().getBlock();
            return switch (block.getType()) {
                case CHEST -> "сундук/сундук-ловушку";
                case BARREL -> "бочку";
                case SHULKER_BOX -> "шалкер";
                case DISPENSER -> "раздатчик";
                case DROPPER -> "выбрасыватель";
                case HOPPER -> "воронку";
                case FURNACE -> "печь";
                case BLAST_FURNACE -> "доменную печь";
                case SMOKER -> "коптильню";
                case BREWING_STAND -> "зельеварочную стойку";
                case BEACON -> "маяк";
                case ANVIL -> "наковальню";
                case GRINDSTONE -> "точило";
                case STONECUTTER -> "камнерез";
                case LOOM -> "ткацкий станок";
                case CARTOGRAPHY_TABLE -> "картографический стол";
                case SMITHING_TABLE -> "кузнечный стол";
                case ENCHANTING_TABLE -> "стол зачарований";
                default -> type.name().toLowerCase();
            };
        }
        return type.name().toLowerCase();
    }
}
