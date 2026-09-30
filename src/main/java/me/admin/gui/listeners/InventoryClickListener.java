package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PaginatedGUI;
import me.admin.gui.gui.ReadOnlyInventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class InventoryClickListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    public InventoryClickListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof ReadOnlyInventoryHolder) {
            // Immutable snapshots reject every inventory mutation path, including
            // bottom-inventory shift clicks, number/offhand swaps and collection.
            event.setCancelled(true);
            return;
        }

        PaginatedGUI gui = plugin.getGuiManager().resolve(player, event.getView().getTopInventory());
        if (gui == null) return;

        if (event.getView().getTopInventory().getHolder() instanceof Player) {
            return; // real player inventory — allow editing
        }

        // Cancel the complete view, not only its top half. This also blocks
        // shift-transfer from the player's inventory, number-key/hotbar swaps,
        // offhand swaps, double-click collection and clicks with an empty slot.
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        gui.onClick(event.getSlot(), click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT,
                click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof ReadOnlyInventoryHolder) {
            event.setCancelled(true);
            return;
        }
        PaginatedGUI gui = plugin.getGuiManager().resolve(player, event.getView().getTopInventory());
        if (gui != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getGuiManager().closed(player, event.getView().getTopInventory());
        // Keep navigation during an inventory-to-inventory transition, but do
        // not retain GUI instances after a player actually closes the menu.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !plugin.getGuiManager().hasGUI(player)) {
                plugin.getGuiManager().cleanup(player.getUniqueId());
            }
        });
    }
}
