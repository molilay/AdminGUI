package me.admin.gui.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Typed holder binding a concrete inventory view to one GUI session and owner. */
public final class AMGuiHolder implements InventoryHolder {
    public enum Access { MANAGED_READ_ONLY }

    private final UUID sessionId;
    private final UUID ownerId;
    private final PaginatedGUI menu;
    private final Access access;
    private Inventory inventory;

    AMGuiHolder(UUID sessionId, UUID ownerId, PaginatedGUI menu, Access access) {
        this.sessionId = sessionId;
        this.ownerId = ownerId;
        this.menu = menu;
        this.access = access;
    }

    void attach(Inventory inventory) { this.inventory = inventory; }
    public UUID sessionId() { return sessionId; }
    public UUID ownerId() { return ownerId; }
    public PaginatedGUI menu() { return menu; }
    public Access access() { return access; }

    @Override public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("GUI inventory is not attached yet");
        return inventory;
    }
}
