package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerHistoryGUI extends PaginatedGUI {

    private static final int HISTORY_PAGE_CAPACITY = 44;

    private final OfflinePlayer target;
    private List<LogEntry> logs;
    private String filterType = "all";

    public PlayerHistoryGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        return "&8История: " + (target.getName() != null ? target.getName() : "?");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        String targetName = target.getName();
        if (targetName == null) return;
        int queryLimit = Math.max(100, plugin.getConfig().getInt("database.gui-query-limit", 5000));
        logs = plugin.getDatabaseManager().getLogs(queryLimit, 0, targetName).stream()
                .filter(l -> l.getTarget().equalsIgnoreCase(targetName)).collect(Collectors.toList());

        if (!filterType.equals("all")) {
            logs = logs.stream()
                    .filter(l -> l.getType().equalsIgnoreCase(filterType))
                    .collect(Collectors.toList());
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
            case "warn" -> Material.PAPER;
            case "mute" -> Material.JUKEBOX;
            case "unmute" -> Material.NOTE_BLOCK;
            default -> Material.PAPER;
        };

        String typeName = switch (entry.getType().toLowerCase()) {
            case "ban" -> "&cБан";
            case "tempban" -> "&cВременный бан";
            case "unban" -> "&aРазбан";
            case "kick" -> "&6Кик";
            case "freeze" -> "&bЗаморозка";
            case "unfreeze" -> "&aРазморозка";
            case "warn" -> "&eВарн";
            case "mute" -> "&cМут";
            case "unmute" -> "&aРазмьючен";
            default -> "&7" + entry.getType();
        };

        ItemBuilder builder = new ItemBuilder(icon)
                .name(typeName)
                .lore(
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
        SoundUtil.click(viewer);
        if (slot == 0) {
            new PlayerCardGUI(plugin, viewer, target).open();
            return;
        }
        if (slot >= 45) {
            if (slot == SLOT_CLOSE) {
                new PlayerCardGUI(plugin, viewer, target).open();
                return;
            }
            if (slot == SLOT_ALL) { filterType = "all"; page = 0; refresh(); return; }
            if (slot == SLOT_BANS) { filterType = "ban"; page = 0; refresh(); return; }
            if (slot == SLOT_KICKS) { filterType = "kick"; page = 0; refresh(); return; }
            if (slot == SLOT_FREEZE) { filterType = "freeze"; page = 0; refresh(); return; }
            if (slot == SLOT_EXPORT) { exportHistory(); return; }
            if (slot == SLOT_PREV && page > 0) { page--; refresh(); return; }
            if (slot == SLOT_NEXT && (page + 1) * HISTORY_PAGE_CAPACITY < contentItems.size()) { page++; refresh(); return; }
            if (slot == SLOT_MAIN_MENU) { openHome(); }
        }
    }

    private void exportHistory() {
        String targetName = target.getName();
        if (targetName == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"player\": \"").append(escapeJson(targetName)).append("\",\n");
            json.append("  \"exported\": \"").append(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
            json.append("  \"totalActions\": ").append(logs.size()).append(",\n");
            json.append("  \"entries\": [\n");
            for (int i = 0; i < logs.size(); i++) {
                me.admin.gui.database.LogEntry entry = logs.get(i);
                json.append("    {\n");
                json.append("      \"type\": \"").append(escapeJson(entry.getType())).append("\",\n");
                json.append("      \"staff\": \"").append(escapeJson(entry.getModerator())).append("\",\n");
                json.append("      \"reason\": \"").append(escapeJson(entry.getReason())).append("\",\n");
                json.append("      \"date\": \"").append(entry.getDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
                json.append("      \"duration\": ").append(entry.getDuration()).append("\n");
                json.append("    }");
                if (i < logs.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}\n");

            File exportDir = new File(plugin.getDataFolder(), "exports");
            exportDir.mkdirs();
            File exportFile = new File(exportDir, targetName + "_history.json");
            try {
                Files.writeString(exportFile.toPath(), json.toString());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.sendMessage("§aИстория экспортирована в /exports/" + targetName + "_history.json");
                });
            } catch (IOException ex) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    viewer.sendMessage("§cОшибка экспорта: " + ex.getMessage());
                });
            }
        });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final int SLOT_ALL = 46;
    private static final int SLOT_BANS = 47;
    private static final int SLOT_KICKS = 49;
    private static final int SLOT_FREEZE = 50;
    private static final int SLOT_EXPORT = 51;

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));

        int start = page * HISTORY_PAGE_CAPACITY;
        int end = Math.min(start + HISTORY_PAGE_CAPACITY, contentItems.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start + 1, contentItems.get(i));
        }

        for (int i = 45; i < SIZE; i++) {
            inv.setItem(i, ItemBuilder.createFiller());
        }

        if (page > 0) inv.setItem(SLOT_PREV, ItemBuilder.createPreviousButton());
        if (end < contentItems.size()) inv.setItem(SLOT_NEXT, ItemBuilder.createNextButton());
        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());

        inv.setItem(SLOT_ALL, new ItemBuilder(Material.COMPASS)
                .name("&fВсе").glowing(filterType.equals("all")).build());
        inv.setItem(SLOT_BANS, new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("&cБаны").glowing(filterType.equals("ban")).build());
        inv.setItem(SLOT_KICKS, new ItemBuilder(Material.IRON_DOOR)
                .name("&6Кики").glowing(filterType.equals("kick")).build());
        inv.setItem(SLOT_FREEZE, new ItemBuilder(Material.PACKED_ICE)
                .name("&bЗаморозки").glowing(filterType.equals("freeze")).build());
        inv.setItem(SLOT_EXPORT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&bЭкспорт в JSON")
                .lore("&7Сохранить историю в файл")
                .glowing()
                .build());

        inv.setItem(0, new ItemBuilder(Material.ARROW)
                .name("&7← Назад к карточке игрока")
                .build());

        return inv;
    }

    @Override
    public void open() {
        super.open();
    }
}
