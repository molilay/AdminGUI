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

import java.util.List;
import java.util.stream.Collectors;

public class PlayerHistoryGUI extends PaginatedGUI {

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
        logs = plugin.getDatabaseManager().getAllLogs().stream()
                .filter(l -> l.getTarget().equalsIgnoreCase(target.getName()))
                .collect(Collectors.toList());

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
        if (slot >= 45) {
            if (slot == SLOT_CLOSE) {
                new PlayerCardGUI(plugin, viewer, target).open();
                return;
            }
            if (slot == SLOT_ALL) { filterType = "all"; page = 0; refresh(); return; }
            if (slot == SLOT_BANS) { filterType = "ban"; page = 0; refresh(); return; }
            if (slot == SLOT_KICKS) { filterType = "kick"; page = 0; refresh(); return; }
            if (slot == SLOT_FREEZE) { filterType = "freeze"; page = 0; refresh(); return; }
            if (slot == SLOT_WARN) { filterType = "warn"; page = 0; refresh(); return; }
            handlePaginatedClick(slot);
        }
    }

    private static final int SLOT_ALL = 46;
    private static final int SLOT_BANS = 47;
    private static final int SLOT_KICKS = 49;
    private static final int SLOT_FREEZE = 50;
    private static final int SLOT_WARN = 51;

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, getTitle());

        int start = page * MAX_ITEMS_PER_PAGE;
        int end = Math.min(start + MAX_ITEMS_PER_PAGE, contentItems.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, contentItems.get(i));
        }

        for (int i = 45; i < SIZE; i++) {
            inv.setItem(i, ItemBuilder.createFiller());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) contentItems.size() / MAX_ITEMS_PER_PAGE));
        if (page > 0) inv.setItem(SLOT_PREV, ItemBuilder.createPreviousButton());
        inv.setItem(SLOT_INFO, ItemBuilder.createPageInfo(page, totalPages));
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
        inv.setItem(SLOT_WARN, new ItemBuilder(Material.PAPER)
                .name("&eВарны").glowing(filterType.equals("warn")).build());

        inv.setItem(0, new ItemBuilder(Material.ARROW)
                .name("&7← Назад к карточке игрока")
                .build());

        return inv;
    }

    @Override
    public void open() {
        viewer.openInventory(buildInventory());
        plugin.getGuiManager().register(viewer.getUniqueId(), this);
    }
}
