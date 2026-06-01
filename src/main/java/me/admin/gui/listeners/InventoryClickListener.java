package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PaginatedGUI;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        PaginatedGUI gui = plugin.getGuiManager().getOpenGUI(player);
        if (gui == null) return;

        event.setCancelled(true);

        if (plugin.getConfig().getBoolean("gui.sound-enabled", true)) {
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            } catch (Exception ignored) {}
        }

        gui.onClick(event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PaginatedGUI gui = plugin.getGuiManager().getOpenGUI(player);
        if (gui != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getGuiManager().unregister(player.getUniqueId());
    }
}
