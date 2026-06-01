package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainMenu extends PaginatedGUI {

    private static final int SLOT_PLAYERS_ONLINE = 20;
    private static final int SLOT_PLAYERS_OFFLINE = 22;
    private static final int SLOT_GROUPS = 24;
    private static final int SLOT_LOGS = 31;
    private static final int SLOT_STAFFCHAT = 15;
    private static final int SLOT_TPS = 4;
    private static final int SLOT_BANS = 33;
    private static final int SLOT_INVENTORY_ROLLBACK = 29;
    private static final int SLOT_WARN = 25;
    private static final int SLOT_STATS = 40;
    private static final int SLOT_STAFF_LIST = 41;

    public MainMenu(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-main");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
    }

    @Override
    public void onClick(int slot) {
        SoundUtil.click(viewer);
        switch (slot) {
            case SLOT_PLAYERS_ONLINE -> new PlayerListGUI(plugin, viewer, true).open();
            case SLOT_PLAYERS_OFFLINE -> new PlayerListGUI(plugin, viewer, false).open();
            case SLOT_GROUPS -> {
                if (viewer.hasPermission("amgui.groups")) {
                    new GroupListGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_LOGS -> {
                if (viewer.hasPermission("amgui.logs")) {
                    new LogsGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_STAFFCHAT -> {
                if (viewer.hasPermission("amgui.staffchat")) {
                    plugin.getStaffChatManager().toggle(viewer);
                    open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_BANS -> {
                if (viewer.hasPermission("amgui.ban")) {
                    new ActiveBansGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_INVENTORY_ROLLBACK -> {
                if (viewer.hasPermission("amgui.rollback")) {
                    new PlayerListGUI(plugin, viewer, true).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_WARN -> {
                if (viewer.hasPermission("amgui.warn")) {
                    new PlayerListGUI(plugin, viewer, true).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_CLOSE -> close();
        }
    }

    @Override
    public void open() {
        viewer.openInventory(buildInventory());
        plugin.getGuiManager().register(viewer.getUniqueId(), this);
    }

    @Override
    protected Inventory buildInventory() {
        int totalOnline = plugin.getServer().getOnlinePlayers().size();
        double[] tps = plugin.getServer().getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);

        List<LogEntry> allLogs = plugin.getDatabaseManager().getAllLogs();
        long todayBans = allLogs.stream()
                .filter(l -> (l.getType().equals("ban") || l.getType().equals("tempban"))
                        && l.getDate().toLocalDate().equals(LocalDate.now()))
                .count();
        long totalBans = allLogs.stream()
                .filter(l -> l.getType().equals("ban") || l.getType().equals("tempban"))
                .count();
        long totalMutes = allLogs.stream().filter(l -> l.getType().equals("mute")).count();
        long totalWarns = allLogs.stream().filter(l -> l.getType().equals("warn")).count();

        long staffOnline = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("amgui.staffchat"))
                .count();

        int activeBans = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries().size();

        Inventory inv = Bukkit.createInventory(null, SIZE, getTitle());

        inv.setItem(SLOT_PLAYERS_ONLINE, new ItemBuilder(Material.LIME_WOOL)
                .name("&aИгроки онлайн &7(" + totalOnline + ")")
                .lore("&7Кликните для просмотра", "&7онлайн-игроков и управления")
                .glowing()
                .build());

        inv.setItem(SLOT_PLAYERS_OFFLINE, new ItemBuilder(Material.GRAY_WOOL)
                .name("&7Оффлайн игроки")
                .lore("&7Поиск оффлайн-игроков", "&7по нику и управление")
                .build());

        inv.setItem(SLOT_GROUPS, new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&bУправление ролями")
                .lore("&7Создание, редактирование", "&7и назначение групп LuckPerms")
                .build());

        inv.setItem(SLOT_LOGS, new ItemBuilder(Material.BOOKSHELF)
                .name("&eЛоги наказаний")
                .lore("&7Просмотр истории", "&7банов, киков и заморозок")
                .build());

        String tpsColor = tps[0] > 18.0 ? "&a" : tps[0] > 15.0 ? "&e" : "&c";
        inv.setItem(SLOT_TPS, new ItemBuilder(Material.CLOCK)
                .name("&6Статус сервера")
                .lore(
                        "&7TPS: " + tpsColor + String.format("%.1f", tps[0]),
                        "&7Память: &f" + usedMem + "&7/&f" + maxMem + " MB",
                        "&7Всего игроков: &f" + totalOnline + "/" + plugin.getServer().getMaxPlayers()
                )
                .build());

        inv.setItem(SLOT_STAFFCHAT, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&bStaffChat")
                .lore(
                        plugin.getStaffChatManager().isToggled(viewer) ? "&a✓ Включён" : "&7✗ Выключен",
                        "&7Кликните для переключения",
                        "&7Либо используйте /sc <сообщение>"
                )
                .glowing(plugin.getStaffChatManager().isToggled(viewer))
                .build());

        inv.setItem(SLOT_BANS, new ItemBuilder(Material.IRON_BARS)
                .name("&cАктивные баны")
                .lore("&7Список забаненных игроков", "&7и управление ими")
                .build());

        inv.setItem(SLOT_WARN, new ItemBuilder(Material.PAPER)
                .name("&eПредупреждения (Warns)")
                .lore("&7Выдать предупреждение игроку", "&73 варна = авто-бан")
                .build());

        inv.setItem(SLOT_INVENTORY_ROLLBACK, new ItemBuilder(Material.CHEST)
                .name("&6Откат инвентаря")
                .lore("&7Восстановить инвентарь игрока", "&7из сохранённых снапшотов")
                .build());

        inv.setItem(SLOT_STATS, new ItemBuilder(Material.GOLD_NUGGET)
                .name("&6Статистика наказаний")
                .lore(
                        "&7Банов сегодня: &c" + todayBans,
                        "&7Всего банов: &c" + totalBans,
                        "&7Активных банов: &c" + activeBans,
                        "&7Мьютов: &e" + totalMutes,
                        "&7Варнов: &e" + totalWarns
                )
                .build());

        inv.setItem(SLOT_STAFF_LIST, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&bПерсонал онлайн &7(" + staffOnline + ")")
                .lore("&7Кликните для списка")
                .build());

        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createFiller());
            }
        }

        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inv;
    }
}
