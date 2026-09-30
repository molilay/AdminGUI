package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class ConfirmGUI {

    private final AdvancedModeratorGUI plugin;
    private final Player viewer;
    private final String message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;
    private static final int SLOT_MESSAGE = 13;

    public ConfirmGUI(AdvancedModeratorGUI plugin, Player viewer, String message, Runnable onConfirm) {
        this(plugin, viewer, message, onConfirm, () -> {});
    }

    public ConfirmGUI(AdvancedModeratorGUI plugin, Player viewer, String message, Runnable onConfirm, Runnable onCancel) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public void open() {
        String title = plugin.getConfigManager().getGuiTitle("title-confirm");
        Inventory inv = Bukkit.createInventory(null, 27, me.admin.gui.utils.TextUtil.legacy(title));

        inv.setItem(SLOT_CONFIRM, ItemBuilder.createConfirmButton());
        inv.setItem(SLOT_CANCEL, ItemBuilder.createCancelButton());

        if (message != null && !message.isEmpty()) {
            inv.setItem(SLOT_MESSAGE, new ItemBuilder(Material.OAK_SIGN)
                    .name("&6" + message.replace("§", "&"))
                    .lore("&7Выберите действие ниже")
                    .glowing()
                    .build());
        }

        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createFiller());
            }
        }

        PaginatedGUI confirmMenu = new PaginatedGUI(plugin, viewer) {
            @Override public String getTitle() { return title; }
            @Override public void buildContent() {}
            @Override protected Inventory buildInventory() { return inv; }
            @Override public void refresh() {}
            @Override
            public void onClick(int slot) {
                Runnable callback;
                if (slot == SLOT_CONFIRM) callback = onConfirm;
                else if (slot == SLOT_CANCEL) callback = onCancel;
                else return;
                // Close the confirmation inventory first. The callback may open
                // another GUI and must never have that new screen closed here.
                plugin.getGuiManager().unregister(viewer.getUniqueId());
                viewer.closeInventory();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (viewer.isOnline()) callback.run();
                });
            }
        };
        plugin.getGuiManager().open(viewer, confirmMenu, inv, true);
    }
}
