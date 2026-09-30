package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AuditManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;

import java.util.List;

public final class AuditGUI extends PaginatedGUI {

    private List<AuditManager.AuditEntry> entries = List.of();

    public AuditGUI(AdvancedModeratorGUI plugin, org.bukkit.entity.Player viewer) {
        super(plugin, viewer);
    }

    @Override public String getTitle() { return "§8Защищённый аудит"; }

    @Override
    public void buildContent() {
        contentItems.clear();
        entries = plugin.getAuditManager().getRecent();
        boolean revealIp = viewer.hasPermission("amgui.viewip");
        for (AuditManager.AuditEntry entry : entries) {
            String target = entry.targetName().isBlank() ? "—" : entry.targetName();
            String details = entry.details();
            if (!revealIp) {
                target = me.admin.gui.utils.IpPrivacyUtil.redactText(target);
                details = me.admin.gui.utils.IpPrivacyUtil.redactText(details);
            }
            contentItems.add(new ItemBuilder(Material.PAPER)
                    .name("&f" + entry.actorName() + " &7→ &e" + entry.action())
                    .lore("&7Цель: &f" + target,
                            "&7Детали: &f" + truncate(details, 120),
                            "&7Время: &f" + TimeUtils.formatLogTime(entry.timestamp()),
                            "&8SHA-256: " + entry.hash().substring(0, 16) + "…").build());
        }
    }

    @Override
    protected org.bukkit.inventory.Inventory buildInventory() {
        var inventory = super.buildInventory();
        boolean valid = plugin.getAuditManager().isIntegrityValid();
        inventory.setItem(50, new ItemBuilder(valid ? Material.EMERALD : Material.REDSTONE)
                .name(valid ? "&a✓ Цепочка аудита цела" : "&c✗ Целостность нарушена")
                .lore("&7Записи связаны цепочкой SHA-256").glowing(valid).build());
        return inventory;
    }

    @Override public void onClick(int slot) { if (slot >= 45) handlePaginatedClick(slot); }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
