package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class LogsGUI extends PaginatedGUI {

    private String filterType = "all";
    private String filterPlayer = "";
    private boolean sortAscending = false;

    private static final int SLOT_EXPORT = 4;
    private static final int SLOT_CLEAR = 8;
    private static final int SLOT_ALL = 46;
    private static final int SLOT_BANS = 47;
    private static final int SLOT_KICKS = 50;
    private static final int SLOT_FREEZE = 51;
    private static final int SLOT_SEARCH = 44;
    private static final int SLOT_SORT = 43;

    private static final int EFFECTIVE_CAPACITY = 42; // 45 slots - SLOT_EXPORT(4) - SLOT_CLEAR(8) - SLOT_SORT(43)

    public LogsGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    public LogsGUI(AdvancedModeratorGUI plugin, Player viewer, String filterPlayer) {
        super(plugin, viewer);
        this.filterPlayer = filterPlayer;
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-logs");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        List<LogEntry> logs = plugin.getDatabaseManager().getAllLogs();

        if (!filterType.equals("all")) {
            logs = logs.stream()
                    .filter(l -> l.getType().equalsIgnoreCase(filterType))
                    .collect(Collectors.toList());
        }
        if (!filterPlayer.isEmpty()) {
            logs = logs.stream()
                    .filter(l -> l.getTarget().toLowerCase().contains(filterPlayer.toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (sortAscending) {
            logs = new java.util.ArrayList<>(logs);
            java.util.Collections.reverse(logs);
        }

        for (LogEntry entry : logs) {
            contentItems.add(buildLogItem(entry));
        }
    }

    private ItemStack buildLogItem(LogEntry entry) {
        Material icon = switch (entry.getType().toLowerCase()) {
            case "ban" -> Material.REDSTONE_BLOCK;
            case "tempban" -> Material.REDSTONE_ORE;
            case "unban" -> Material.EMERALD_BLOCK;
            case "kick" -> Material.IRON_DOOR;
            case "freeze" -> Material.PACKED_ICE;
            case "unfreeze" -> Material.ICE;
            default -> Material.PAPER;
        };

        String typeName = switch (entry.getType().toLowerCase()) {
            case "ban" -> "&cБан";
            case "tempban" -> "&cВременный бан";
            case "unban" -> "&aРазбан";
            case "kick" -> "&6Кик";
            case "freeze" -> "&bЗаморозка";
            case "unfreeze" -> "&aРазморозка";
            default -> "&7" + entry.getType();
        };

        ItemBuilder builder = new ItemBuilder(icon)
                .name(typeName)
                .lore(
                        "&7Игрок: &f" + entry.getTarget(),
                        "&7Модератор: &f" + entry.getModerator(),
                        "&7Причина: &f" + entry.getReason(),
                        "&7Дата: &f" + TimeUtils.formatLogTime(entry.getDate())
                );

        if (entry.getDuration() > 0) {
            builder.lore("&7Длительность: &f" + TimeUtils.formatDuration(entry.getDuration()));
        }

        return builder.build();
    }

    @Override
    public void onClick(int slot) {
        me.admin.gui.utils.SoundUtil.click(viewer);
        if (slot >= 45) {
            switch (slot) {
                case SLOT_CLOSE -> close();
                case SLOT_ALL -> {
                    filterType = "all"; page = 0; refresh();
                }
                case SLOT_BANS -> {
                    filterType = "ban"; page = 0; refresh();
                }
                case SLOT_KICKS -> {
                    filterType = "kick"; page = 0; refresh();
                }
                case SLOT_FREEZE -> {
                    filterType = "freeze"; page = 0; refresh();
                }
                case SLOT_MAIN_MENU -> {
                    plugin.getGuiManager().unregister(viewer.getUniqueId());
                    new MainMenu(plugin, viewer).open();
                }
                case SLOT_SEARCH -> searchByPlayer();
                case SLOT_SORT -> { sortAscending = !sortAscending; page = 0; refresh(); }
                case SLOT_PREV -> { if (page > 0) { page--; refresh(); } }
                case SLOT_NEXT -> { if ((page + 1) * EFFECTIVE_CAPACITY < contentItems.size()) { page++; refresh(); } }
            }
            return;
        }

        if (slot == SLOT_EXPORT) exportLogs();
        else if (slot == SLOT_CLEAR) clearLogs();
    }

    private void searchByPlayer() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите ник игрока для поиска:", input -> {
            filterPlayer = input;
            page = 0;
            open();
        });
    }

    private void exportLogs() {
        List<LogEntry> logs = plugin.getDatabaseManager().getAllLogs();
        if (!filterType.equals("all")) {
            logs = logs.stream()
                    .filter(l -> l.getType().equalsIgnoreCase(filterType))
                    .collect(Collectors.toList());
        }
        if (!filterPlayer.isEmpty()) {
            logs = logs.stream()
                    .filter(l -> l.getTarget().toLowerCase().contains(filterPlayer.toLowerCase()))
                    .collect(Collectors.toList());
        }

        File dir = new File(plugin.getDataFolder(), "exports");
        dir.mkdirs();
        File file = new File(dir, "logs_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");

        try (FileWriter fw = new FileWriter(file)) {
            fw.write("=== Export Logs ===\n");
            fw.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            fw.write("Filter: type=" + filterType + " player=" + (filterPlayer.isEmpty() ? "all" : filterPlayer) + "\n");
            fw.write("Total: " + logs.size() + "\n\n");
            for (LogEntry e : logs) {
                fw.write("[" + e.getType() + "] " + e.getTarget() + " | " + e.getModerator() + " | " + e.getReason() +
                        " | " + TimeUtils.formatLogTime(e.getDate()));
                if (e.getDuration() > 0) fw.write(" | " + TimeUtils.formatDuration(e.getDuration()));
                fw.write("\n");
            }
            viewer.sendMessage("§a✓ Логи экспортированы: " + file.getName());
        } catch (IOException ex) {
            viewer.sendMessage("§cОшибка экспорта логов.");
        }
    }

    private void clearLogs() {
        new ConfirmGUI(plugin, viewer, "§cОчистить все логи?", () -> {
            plugin.getDatabaseManager().clearLogs();
            page = 0;
            refresh();
            viewer.sendMessage("§a✓ Логи очищены.");
        }).open();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, getTitle());

        int start = page * EFFECTIVE_CAPACITY;
        int end = Math.min(start + EFFECTIVE_CAPACITY, contentItems.size());
        int placeIndex = 0;
        for (int i = start; i < end; i++) {
            while (placeIndex == SLOT_EXPORT || placeIndex == SLOT_CLEAR || placeIndex == SLOT_SEARCH || placeIndex == SLOT_SORT) placeIndex++;
            inv.setItem(placeIndex, contentItems.get(i));
            placeIndex++;
        }

        for (int i = 45; i < SIZE; i++) {
            inv.setItem(i, ItemBuilder.createFiller());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) contentItems.size() / EFFECTIVE_CAPACITY));
        if (page > 0) inv.setItem(SLOT_PREV, ItemBuilder.createPreviousButton());
        inv.setItem(49, ItemBuilder.createPageInfo(page, totalPages));
        if (end < contentItems.size()) inv.setItem(SLOT_NEXT, ItemBuilder.createNextButton());
        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());

        inv.setItem(SLOT_ALL, new ItemBuilder(Material.COMPASS)
                .name("&fВсе")
                .lore("&7Показать все логи")
                .glowing(filterType.equals("all"))
                .build());

        inv.setItem(SLOT_BANS, new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("&cБаны")
                .lore("&7Показать баны")
                .glowing(filterType.equals("ban"))
                .build());

        inv.setItem(SLOT_KICKS, new ItemBuilder(Material.IRON_DOOR)
                .name("&6Кики")
                .lore("&7Показать кики")
                .glowing(filterType.equals("kick"))
                .build());

        inv.setItem(SLOT_FREEZE, new ItemBuilder(Material.PACKED_ICE)
                .name("&bЗаморозки")
                .lore("&7Показать заморозки")
                .glowing(filterType.equals("freeze"))
                .build());

        inv.setItem(SLOT_SEARCH, new ItemBuilder(Material.OAK_SIGN)
                .name("&eПоиск по игроку")
                .lore("&7Фильтр: " + (filterPlayer.isEmpty() ? "&oнет" : "&f" + filterPlayer))
                .build());

        inv.setItem(SLOT_SORT, new ItemBuilder(Material.HOPPER)
                .name("&fСортировка: " + (sortAscending ? "&a↑ старые" : "&c↓ новые"))
                .lore("&7Кликните для переключения")
                .build());

        inv.setItem(SLOT_EXPORT, new ItemBuilder(Material.HOPPER)
                .name("&eЭкспорт логов")
                .lore("&7Сохранить отфильтрованные логи в файл")
                .build());

        inv.setItem(SLOT_CLEAR, new ItemBuilder(Material.LAVA_BUCKET)
                .name("&cОчистить логи")
                .lore("&7Удалить все логи из базы данных")
                .build());

        return inv;
    }
}
