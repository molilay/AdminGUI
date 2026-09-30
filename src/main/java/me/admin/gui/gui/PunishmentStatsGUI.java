package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;


public class PunishmentStatsGUI extends PaginatedGUI {

    public PunishmentStatsGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-punishment-stats");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
    }

    @Override
    protected Inventory buildInventory() {
        var stats = plugin.getDatabaseManager().getDetailedStats();
        var zero = new me.admin.gui.database.DatabaseManager.TypeCounts(0, 0, 0, 0);
        var bans = stats.types().getOrDefault("ban", zero)
                .plus(stats.types().getOrDefault("tempban", zero));
        var mutes = stats.types().getOrDefault("mute", zero);
        var warns = stats.types().getOrDefault("warn", zero);
        var kicks = stats.types().getOrDefault("kick", zero);

        long totalBans = bans.total(), totalMutes = mutes.total(), totalWarns = warns.total(), totalKicks = kicks.total();
        long todayBans = bans.today(), todayMutes = mutes.today(), todayWarns = warns.today(), todayKicks = kicks.today();
        long weekBans = bans.week(), weekMutes = mutes.week(), weekWarns = warns.week(), weekKicks = kicks.week();
        long monthBans = bans.month(), monthMutes = mutes.month(), monthWarns = warns.month(), monthKicks = kicks.month();

        int activeBans = me.admin.gui.utils.BanService.activeProfileBanCount();
        int activeMutes = plugin.getMuteManager().getMutedEntries().size();

        var topStaff = stats.topStaff();

        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));

        fillRow(inv, 0, Material.CYAN_STAINED_GLASS_PANE, "§b≡ Общая статистика");

        inv.setItem(0, new ItemBuilder(Material.BARRIER)
                .name("&cВсего банов")
                .lore("&7За всё время: &f" + totalBans,
                        "&7За месяц: &f" + monthBans,
                        "&7За неделю: &f" + weekBans,
                        "&7За сегодня: &f" + todayBans)
                .build());

        inv.setItem(1, new ItemBuilder(Material.PAPER)
                .name("&eВсего мьютов")
                .lore("&7За всё время: &f" + totalMutes,
                        "&7За месяц: &f" + monthMutes,
                        "&7За неделю: &f" + weekMutes,
                        "&7За сегодня: &f" + todayMutes)
                .build());

        inv.setItem(2, new ItemBuilder(Material.BOOK)
                .name("&6Всего варнов")
                .lore("&7За всё время: &f" + totalWarns,
                        "&7За месяц: &f" + monthWarns,
                        "&7За неделю: &f" + weekWarns,
                        "&7За сегодня: &f" + todayWarns)
                .build());

        inv.setItem(3, new ItemBuilder(Material.IRON_SWORD)
                .name("&cВсего киков")
                .lore("&7За всё время: &f" + totalKicks,
                        "&7За месяц: &f" + monthKicks,
                        "&7За неделю: &f" + weekKicks,
                        "&7За сегодня: &f" + todayKicks)
                .build());

        fillRow(inv, 1, Material.LIME_STAINED_GLASS_PANE, "&a≡ Сегодня");

        inv.setItem(9, new ItemBuilder(Material.BARRIER)
                .name("&cБанов сегодня")
                .lore("&f" + todayBans)
                .build());

        inv.setItem(10, new ItemBuilder(Material.PAPER)
                .name("&eМьютов сегодня")
                .lore("&f" + todayMutes)
                .build());

        inv.setItem(11, new ItemBuilder(Material.BOOK)
                .name("&6Варнов сегодня")
                .lore("&f" + todayWarns)
                .build());

        inv.setItem(12, new ItemBuilder(Material.IRON_SWORD)
                .name("&cКиков сегодня")
                .lore("&f" + todayKicks)
                .build());

        fillRow(inv, 2, Material.ORANGE_STAINED_GLASS_PANE, "§6≡ Активные наказания");

        inv.setItem(18, new ItemBuilder(Material.IRON_BARS)
                .name("&cАктивных банов")
                .lore("&f" + activeBans)
                .build());

        inv.setItem(19, new ItemBuilder(Material.PAPER)
                .name("&eАктивных мьютов")
                .lore("&f" + activeMutes)
                .build());

        fillRow(inv, 3, Material.ORANGE_STAINED_GLASS_PANE, "§6≡ Топ-5 персонала");

        for (int i = 0; i < Math.min(5, topStaff.size()); i++) {
            int slot = 27 + i;
            me.admin.gui.database.DatabaseManager.StaffCount entry = topStaff.get(i);
            inv.setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&e#" + (i + 1) + " &f" + entry.name())
                    .lore("&7Действий: &f" + entry.actions())
                    .build());
        }

        fillBorderSlots(inv);
        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());

        return inv;
    }

    @Override
    public void onClick(int slot) {
        if (slot == SLOT_MAIN_MENU) {
            openHome();
        } else if (slot == SLOT_CLOSE) {
            close();
        }
    }

    private void fillRow(Inventory inv, int row, Material pane, String name) {
        int start = row * 9;
        int end = start + 9;
        for (int i = start; i < end; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemBuilder(pane).name(name).build());
            }
        }
    }

    private void fillBorderSlots(Inventory inv) {
        for (int i = 45; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createFiller());
            }
        }
    }
}
