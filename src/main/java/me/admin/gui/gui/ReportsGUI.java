package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.ConfirmGUI;
import me.admin.gui.manager.ReportManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ReportsGUI extends PaginatedGUI {

    private List<ReportManager.ReportEntry> reports;
    private String filter = "active";

    private static final int SLOT_RESOLVED_COUNT = 48;
    private static final int SLOT_FILTER_TOGGLE = 50;

    public ReportsGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return "§8Жалобы игроков";
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        reports = switch (filter) {
            case "resolved" -> plugin.getReportManager().getResolved();
            case "all" -> plugin.getReportManager().getAll();
            default -> plugin.getReportManager().getActive();
        };

        for (ReportManager.ReportEntry e : reports) {
            contentItems.add(buildReportItem(e));
        }
    }

    private String getCategoryColor(String category) {
        return switch (category.toLowerCase()) {
            case "hacking" -> "&c";
            case "chat" -> "&b";
            case "griefing" -> "&6";
            default -> "&7";
        };
    }

    private ItemStack buildReportItem(ReportManager.ReportEntry e) {
        String catColor = getCategoryColor(e.category());
        Material icon = switch (e.category().toLowerCase()) {
            case "hacking" -> Material.DIAMOND_SWORD;
            case "chat" -> Material.PAPER;
            case "griefing" -> Material.GRASS_BLOCK;
            default -> Material.PAPER;
        };
        ItemBuilder builder = new ItemBuilder(icon)
                .name("&6#" + e.id() + " &f" + e.target())
                .lore(
                        "&7От: &f" + e.reporter(),
                        "&7Категория: " + catColor + e.category(),
                        "&7Причина: &f" + e.reason(),
                        "&7Время: &f" + e.getFormattedDate()
                );

        if (e.resolved()) {
            builder.lore("", "&a✓ Решено");
        }

        builder.lore("");
        if (!e.resolved() && viewer.hasPermission("amgui.report.resolve")) builder.lore("&eЛКМ — отметить как решено");
        if (viewer.hasPermission("amgui.report.delete")) builder.lore("&cПКМ — удалить");
        if (viewer.hasPermission("amgui.player")) builder.lore("&bShift+ЛКМ — открыть карточку игрока");

        return builder.build();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = super.buildInventory();

        int resolved = plugin.getReportManager().getAll().size() - plugin.getReportManager().getActiveCount();
        inv.setItem(SLOT_RESOLVED_COUNT, new ItemBuilder(Material.BOOK)
                .name("&7Решено: &f" + resolved + " &8/ &7Активно: &c" + plugin.getReportManager().getActiveCount())
                .lore("&7Всего: &f" + plugin.getReportManager().getAll().size())
                .build());

        String filterName = switch (filter) {
            case "resolved" -> "&aРешённые";
            case "all" -> "&eВсе";
            default -> "&cАктивные";
        };
        inv.setItem(SLOT_FILTER_TOGGLE, new ItemBuilder(Material.HOPPER)
                .name("&6Фильтр: " + filterName)
                .lore("&7Нажмите для смены режима", "&7(активные → решённые → все)")
                .glowing()
                .build());

        return inv;
    }

    @Override
    public void onClick(int slot) {
        onClick(slot, false, false);
    }

    @Override
    public void onClick(int slot, boolean shift) {
        onClick(slot, shift, false);
    }

    @Override
    public void onClick(int slot, boolean shift, boolean right) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) { close(); return; }
            if (slot == PaginatedGUI.SLOT_MAIN_MENU) {
                openHome();
                return;
            }
            if (slot == SLOT_FILTER_TOGGLE) {
                filter = switch (filter) {
                    case "active" -> "resolved";
                    case "resolved" -> "all";
                    default -> "active";
                };
                page = 0;
                refresh();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= reports.size()) return;

        ReportManager.ReportEntry entry = reports.get(index);
        SoundUtil.click(viewer);

        if (shift) {
            if (!viewer.hasPermission("amgui.player")) return;
            openPlayerCard(entry.target());
            return;
        }

        if (right) {
            if (!viewer.hasPermission("amgui.report.delete")) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            new ConfirmGUI(plugin, viewer, "§cУдалить жалобу #" + entry.id() + "?",
                () -> {
                    plugin.getReportManager().remove(entry.id(), viewer.getName());
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§c✓ Жалоба #" + entry.id() + " удалена.");
                    refresh();
                },
                () -> refresh()
            ).open();
            return;
        }

        if (entry.resolved()) return;

        if (!viewer.hasPermission("amgui.report.resolve")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        new ConfirmGUI(plugin, viewer, "§aОтметить жалобу #" + entry.id() + " как решённую?",
            () -> {
                plugin.getReportManager().resolve(entry.id(), viewer.getName());
                SoundUtil.success(viewer);
                viewer.sendMessage("§a✓ Жалоба #" + entry.id() + " отмечена как решённая.");
                refresh();
            },
            () -> refresh()
        ).open();
    }

    private void openPlayerCard(String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null && target.isOnline()) {
            new PlayerCardGUI(plugin, viewer, target).open();
            return;
        }
        for (org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                new PlayerCardGUI(plugin, viewer, op).open();
                return;
            }
        }
        viewer.sendMessage("§cИгрок не найден.");
    }

    @Override
    public void open() {
        super.open();
    }
}
