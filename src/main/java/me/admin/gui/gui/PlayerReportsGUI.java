package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.ReportManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Reports scoped to one investigation target. */
public final class PlayerReportsGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private List<ReportManager.ReportEntry> reports = List.of();
    private boolean includeResolved;

    public PlayerReportsGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        return "&8Жалобы: " + targetName();
    }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.report.staff")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        reports = plugin.getReportManager().getAll().stream()
                .filter(report -> report.target().equalsIgnoreCase(targetName()))
                .filter(report -> includeResolved || (!report.resolved() && !report.archived()))
                .toList();
        for (ReportManager.ReportEntry report : reports) {
            ItemBuilder item = new ItemBuilder(report.resolved() ? Material.KNOWLEDGE_BOOK : Material.WRITABLE_BOOK)
                    .name("&6#" + report.id() + " &f" + report.category())
                    .lore("&7От: &f" + report.reporter(), "&7Причина: &f" + report.reason(),
                            "&7Время: &f" + report.getFormattedDate(),
                            "&7Статус: " + (report.resolved() ? "&aрешена" : "&cактивна"));
            if (!report.resolved() && viewer.hasPermission("amgui.report.resolve")) {
                item.lore("", "&eЛКМ — отметить как решённую");
            }
            if (viewer.hasPermission("amgui.report.delete")) item.lore("&cПКМ — удалить");
            contentItems.add(item.build());
        }
        if (reports.isEmpty()) {
            contentItems.add(new ItemBuilder(Material.GRAY_DYE).name("&7Жалоб нет")
                    .lore(includeResolved ? "&7На этого игрока нет жалоб" : "&7Активных жалоб нет").build());
        }
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = super.buildInventory();
        inventory.setItem(48, new ItemBuilder(Material.ARROW).name("&e« Назад к расследованию").build());
        inventory.setItem(50, new ItemBuilder(Material.HOPPER)
                .name(includeResolved ? "&eВсе жалобы" : "&cТолько активные")
                .lore("&7Клик — сменить фильтр").glowing(includeResolved).build());
        return inventory;
    }

    @Override public void onClick(int slot) { onClick(slot, false, false); }

    @Override
    public void onClick(int slot, boolean shift, boolean right) {
        if (slot >= 45) {
            if (slot == 48) {
                if (!plugin.getGuiManager().goBack(viewer)) new InvestigationCenterGUI(plugin, viewer, target).open();
                return;
            }
            if (slot == 50) { includeResolved = !includeResolved; page = 0; refresh(); return; }
            handlePaginatedClick(slot);
            return;
        }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= reports.size()) return;
        ReportManager.ReportEntry report = reports.get(index);
        if (right) {
            if (!viewer.hasPermission("amgui.report.delete")) { deny(); return; }
            new ConfirmGUI(plugin, viewer, "§cУдалить жалобу #" + report.id() + "?", () -> {
                if (!viewer.hasPermission("amgui.report.delete")) { deny(); return; }
                plugin.getReportManager().remove(report.id(), viewer.getName());
                SoundUtil.success(viewer);
                refresh();
            }, this::refresh).open();
            return;
        }
        if (report.resolved()) return;
        if (!viewer.hasPermission("amgui.report.resolve")) { deny(); return; }
        new ConfirmGUI(plugin, viewer, "§aОтметить жалобу #" + report.id() + " как решённую?", () -> {
            if (!viewer.hasPermission("amgui.report.resolve")) { deny(); return; }
            plugin.getReportManager().resolve(report.id(), viewer.getName());
            SoundUtil.success(viewer);
            refresh();
        }, this::refresh).open();
    }

    private String targetName() { return target.getName() == null ? target.getUniqueId().toString() : target.getName(); }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
}
