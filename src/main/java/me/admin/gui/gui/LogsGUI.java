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

    private static final int EFFECTIVE_CAPACITY = 41; // 45 slots - SLOT_EXPORT(4) - SLOT_CLEAR(8) - SLOT_SEARCH(44) - SLOT_SORT(43)

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
        int queryLimit = Math.max(100, plugin.getConfig().getInt("database.gui-query-limit", 5000));
        List<LogEntry> logs = plugin.getDatabaseManager().getLogs(queryLimit, 0,
                filterPlayer.isEmpty() ? null : filterPlayer);

        if (!filterType.equals("all")) {
            logs = logs.stream()
                    .filter(l -> l.getType().equalsIgnoreCase(filterType))
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
        if (slot == SLOT_SEARCH) { searchByPlayer(); return; }
        if (slot == SLOT_SORT) { sortAscending = !sortAscending; page = 0; refresh(); return; }
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
                    openHome();
                }
                case SLOT_PREV -> { if (page > 0) { page--; refresh(); } }
                case SLOT_NEXT -> { if ((page + 1) * EFFECTIVE_CAPACITY < contentItems.size()) { page++; refresh(); } }
            }
            return;
        }

        if (slot == SLOT_EXPORT) {
            if (!viewer.hasPermission("amgui.export")) { viewer.sendMessage("§cНет прав."); return; }
            exportLogs();
        } else if (slot == SLOT_CLEAR) {
            if (!viewer.hasPermission("amgui.admin")) { viewer.sendMessage("§cНет прав."); return; }
            clearLogs();
        }
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
        String selectedType = filterType;
        String selectedPlayer = filterPlayer;
        viewer.sendMessage("§7Экспорт логов запущен…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> exportLogsAsync(selectedType, selectedPlayer));
    }

    private void exportLogsAsync(String selectedType, String selectedPlayer) {
        int queryLimit = Math.max(100, plugin.getConfig().getInt("database.gui-query-limit", 5000));
        List<LogEntry> logs = plugin.getDatabaseManager().getLogs(queryLimit, 0,
                selectedPlayer.isEmpty() ? null : selectedPlayer);
        if (!selectedType.equals("all")) {
            logs = logs.stream().filter(l -> l.getType().equalsIgnoreCase(selectedType)).toList();
        }
        File dir = new File(plugin.getDataFolder(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cНе удалось создать каталог exports."));
            return;
        }
        File file = new File(dir, "logs_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");
        try (FileWriter fw = new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            fw.write("=== Export Logs ===\n");
            fw.write("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            fw.write("Filter: type=" + selectedType + " player=" + (selectedPlayer.isEmpty() ? "all" : selectedPlayer) + "\n");
            fw.write("Total: " + logs.size() + "\n\n");
            for (LogEntry entry : logs) {
                fw.write("[" + entry.getType() + "] " + entry.getTarget() + " | " + entry.getModerator() + " | "
                        + entry.getReason() + " | " + TimeUtils.formatLogTime(entry.getDate()));
                if (entry.getDuration() > 0) fw.write(" | " + TimeUtils.formatDuration(entry.getDuration()));
                fw.write("\n");
            }
            int count = logs.size();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getAuditManager().record(viewer, "logs.export", selectedPlayer, null,
                        "file=" + file.getName() + "; entries=" + count);
                viewer.sendMessage("§a✓ Логи экспортированы: " + file.getName());
            });
        } catch (IOException ex) {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cОшибка экспорта логов."));
        }
    }

    private void clearLogs() {
        new ConfirmGUI(plugin, viewer, "§cОчистить все логи?", () -> {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().clearLogs();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getAuditManager().record(viewer, "logs.clear", "database", null, "all punishment logs");
                    page = 0;
                    refresh();
                    viewer.sendMessage("§a✓ Логи очищены.");
                });
            });
        }).open();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));

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
