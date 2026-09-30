package me.admin.gui.gui;

import me.admin.gui.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Marker holder for immutable inventory snapshots. */
public final class ReadOnlyInventoryHolder implements InventoryHolder {
    private Inventory inventory;

    private ReadOnlyInventoryHolder() {}

    public static Inventory snapshot(int size, String title, ItemStack[] source) {
        ReadOnlyInventoryHolder holder = new ReadOnlyInventoryHolder();
        holder.inventory = Bukkit.createInventory(holder, size, TextUtil.legacy(title));
        ItemStack[] copy = new ItemStack[size];
        if (source != null) {
            int length = Math.min(size, source.length);
            for (int i = 0; i < length; i++) {
                copy[i] = source[i] == null ? null : source[i].clone();
            }
        }
        holder.inventory.setContents(copy);
        return holder.inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
