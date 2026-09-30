package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.IncidentManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Unified YAML/memory timeline with an explicit immutable incident snapshot action. */
public final class IncidentTimelineGUI extends PaginatedGUI {
    private final OfflinePlayer target;
    private List<IncidentManager.TimelineEntry> entries = List.of();

    public IncidentTimelineGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer); this.target = target;
    }
    @Override public String getTitle() { return "&8Incident: " + (target.getName() == null ? target.getUniqueId().toString().substring(0, 8) : target.getName()); }
    @Override public void open() { if (!viewer.hasPermission("amgui.incident.view")) { deny(); return; } super.open(); }
    @Override public void buildContent() {
        entries = plugin.getIncidentManager().timeline(target, 500);
        contentItems.clear();
        boolean compact = plugin.getWorkflowAssignmentManager().isCompact(viewer.getUniqueId());
        for (IncidentManager.TimelineEntry entry : entries) contentItems.add(new ItemBuilder(material(entry.source()))
                .name("&e" + entry.source() + " &8• &f" + TimeUtils.formatLogTime(entry.timestamp()))
                .lore(compact ? List.of("&7" + trim(entry.details(), 54)) : List.of("&7Кто: &f" + entry.actor(),
                        "&7Когда: &f" + TimeUtils.formatLogTime(entry.timestamp()), "", "&7" + trim(entry.details(), 96))).build());
    }
    @Override protected Inventory buildInventory() {
        Inventory inventory = super.buildInventory();
        if (viewer.hasPermission("amgui.incident.snapshot")) inventory.setItem(47, new ItemBuilder(Material.SPYGLASS)
                .name("&aIncident Snapshot").lore("&7Сохраняет YAML-снимок без базы данных", "&7Чат, дела, улики, live-состояние и снапшоты", "&eКлик — подтвердить").build());
        inventory.setItem(48, new ItemBuilder(Material.NETHER_STAR).name("&e⌂ Dashboard").build());
        inventory.setItem(50, new ItemBuilder(Material.KNOWLEDGE_BOOK).name(plugin.getLocalizationManager().get("gui.inbox.legend", "&bЛегенда"))
                .lore("&7Книга — audit/case", "&7Бумага — chat", "&7Сундук — inventory snapshot").build());
        return inventory;
    }
    @Override public void onClick(int slot) {
        if (slot == 47 && viewer.hasPermission("amgui.incident.snapshot")) {
            new ConfirmGUI(plugin, viewer, "§aСоздать Incident Snapshot для " + target.getName() + "?", this::capture, this::open).open(); return;
        }
        if (slot == 48) { new ModeratorDashboardGUI(plugin, viewer).open(); return; }
        if (slot >= 45) handlePaginatedClick(slot);
    }
    private void capture() {
        if (!viewer.hasPermission("amgui.incident.snapshot")) { deny(); return; }
        IncidentManager.SnapshotResult result = plugin.getIncidentManager().capture(target, viewer.getName());
        SoundUtil.success(viewer);
        viewer.sendMessage("§a✓ Incident Snapshot: §f" + result.file().getName());
        viewer.sendMessage("§7Дела=" + result.cases() + ", чат=" + result.messages() + ", улики=" + result.evidence()
                + ", инвентари=" + result.inventorySnapshots() + ". §8БД не использовалась.");
        open();
    }
    private static Material material(String source) { return source.startsWith("case") ? Material.LECTERN : switch (source) {
        case "chat" -> Material.PAPER; case "command" -> Material.COMMAND_BLOCK; case "audit" -> Material.BOOK;
        case "inventory" -> Material.CHEST; default -> Material.FILLED_MAP;
    }; }
    private static String trim(String text, int max) { return text.length() <= max ? text : text.substring(0, max - 1) + "…"; }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
}
