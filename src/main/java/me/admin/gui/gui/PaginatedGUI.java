package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public abstract class PaginatedGUI {

    protected static final int ROWS = 6;
    protected static final int SIZE = ROWS * 9;
    protected static int MAX_ITEMS_PER_PAGE = 45;

    public static void setMaxItemsPerPage(int max) {
        MAX_ITEMS_PER_PAGE = Math.max(9, Math.min(max, 45));
    }

    protected static final int SLOT_PREV = 45;
    protected static final int SLOT_INFO = 49;
    protected static final int SLOT_NEXT = 53;
    protected static final int SLOT_MAIN_MENU = 48;
    protected static final int SLOT_CLOSE = 52;
    protected static final int SLOT_BACK = 18;

    protected final AdvancedModeratorGUI plugin;
    protected final Player viewer;
    protected int page = 0;
    protected List<ItemStack> contentItems = new ArrayList<>();

    protected PaginatedGUI(AdvancedModeratorGUI plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public abstract String getTitle();
    public abstract void buildContent();
    public abstract void onClick(int slot);
    public void onClick(int slot, boolean shift) { onClick(slot); }
    public void onClick(int slot, boolean shift, boolean right) { onClick(slot, shift); }

    public void open() {
        buildContent();
        plugin.getGuiManager().open(viewer, this, buildInventory(), true);
    }

    final void openFromHistory() {
        buildContent();
        plugin.getGuiManager().open(viewer, this, buildInventory(), false);
    }

    public void refresh() {
        buildContent();
        Inventory inv = buildInventory();
        plugin.getGuiManager().open(viewer, this, inv, false);
    }

    public void close() {
        plugin.getGuiManager().unregister(viewer.getUniqueId());
        viewer.closeInventory();
    }

    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));

        for (int i = 45; i < SIZE; i++) {
            inv.setItem(i, ItemBuilder.createFiller());
        }

        // Контент мог уменьшиться (удаление записи) или лимит страницы мог измениться в конфиге:
        // страница всегда приводится к существующему диапазону, иначе GUI открылся бы пустым.
        int totalPages = Math.max(1, (int) Math.ceil((double) contentItems.size() / MAX_ITEMS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * MAX_ITEMS_PER_PAGE;
        int end = Math.min(start + MAX_ITEMS_PER_PAGE, contentItems.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, contentItems.get(i));
        }

        if (page > 0) inv.setItem(SLOT_PREV, ItemBuilder.createPreviousButton());
        inv.setItem(SLOT_INFO, ItemBuilder.createPageInfo(page, totalPages));
        if (end < contentItems.size()) inv.setItem(SLOT_NEXT, ItemBuilder.createNextButton());
        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());

        return inv;
    }

    public boolean isOwner(Player player) {
        return viewer.getUniqueId().equals(player.getUniqueId());
    }

    protected void fillBorder(Inventory inv) {
        for (int i = 45; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createFiller());
            }
        }
    }

    protected void handlePaginatedClick(int slot) {
        if (slot == SLOT_PREV && page > 0) {
            page--;
            SoundUtil.click(viewer);
            refresh();
        } else if (slot == SLOT_NEXT && (page + 1) * MAX_ITEMS_PER_PAGE < contentItems.size()) {
            page++;
            SoundUtil.click(viewer);
            refresh();
        } else if (slot == SLOT_MAIN_MENU) {
            plugin.getGuiManager().home(viewer, new ModeratorDashboardGUI(plugin, viewer));
        } else if (slot == SLOT_CLOSE) {
            close();
        }
    }

    protected void navigateBackOrMainMenu() {
        if (!plugin.getGuiManager().goBack(viewer)) {
            plugin.getGuiManager().home(viewer, new ModeratorDashboardGUI(plugin, viewer));
        }
    }

    protected final void openHome() {
        plugin.getGuiManager().home(viewer, new ModeratorDashboardGUI(plugin, viewer));
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public Player getViewer() { return viewer; }
}
