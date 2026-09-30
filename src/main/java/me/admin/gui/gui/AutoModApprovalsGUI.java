package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AutoModApprovalQueue;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Dedicated, paginated decision screen for destructive AutoMod requests. */
public final class AutoModApprovalsGUI extends PaginatedGUI {
    private List<AutoModApprovalQueue.Request> requests = List.of();

    public AutoModApprovalsGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }
    @Override public String getTitle() { return plugin.getLocalizationManager().get("gui.automod-approvals.title", "&8AutoMod: подтверждения"); }
    @Override public void open() { if (!viewer.hasPermission("amgui.automod.approve")) { deny(); return; } super.open(); }
    @Override public void buildContent() {
        requests = plugin.getAutoModManager().getPendingApprovals();
        contentItems.clear();
        for (AutoModApprovalQueue.Request value : requests) contentItems.add(new ItemBuilder(Material.REDSTONE_TORCH)
                .name("&c#" + value.id() + " " + value.action() + " &f" + value.targetName())
                .lore("&7Правило: &f" + value.ruleId(), "&7Причина: &f" + trim(value.reason(), 64),
                        "&7Создано: &f" + TimeUtils.formatLogTime(value.timestamp()),
                        "&7Длительность: &f" + (value.duration() > 0 ? TimeUtils.formatDuration(value.duration()) : "навсегда"),
                        "", "&aЛКМ — одобрить", "&cПКМ — отклонить", "&7Оба решения требуют подтверждения")
                .glowing().build());
        if (page * MAX_ITEMS_PER_PAGE >= requests.size()) page = 0;
    }

    @Override public void onClick(int slot) { onClick(slot, false, false); }
    @Override public void onClick(int slot, boolean shift, boolean right) {
        if (slot >= 45) { handlePaginatedClick(slot); return; }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= requests.size()) return;
        AutoModApprovalQueue.Request captured = requests.get(index);
        String action = right ? "§cОтклонить" : "§aОдобрить";
        new ConfirmGUI(plugin, viewer, action + " запрос AutoMod #" + captured.id() + "?", () -> decide(captured.id(), !right),
                () -> new AutoModApprovalsGUI(plugin, viewer).open()).open();
    }

    private void decide(int id, boolean approve) {
        if (!viewer.hasPermission("amgui.automod.approve")) { deny(); return; }
        boolean stillExists = plugin.getAutoModManager().getPendingApprovals().stream().anyMatch(value -> value.id() == id);
        if (!stillExists) { viewer.sendMessage("§cЗапрос уже обработан другим сотрудником."); new AutoModApprovalsGUI(plugin, viewer).open(); return; }
        var result = approve ? plugin.getAutoModManager().approvePending(id, viewer) : plugin.getAutoModManager().rejectPending(id, viewer);
        viewer.sendMessage((result.success() ? "§a✓ " : "§c") + result.message());
        if (result.success()) SoundUtil.success(viewer);
        new AutoModApprovalsGUI(plugin, viewer).open();
    }

    private static String trim(String text, int max) { return text.length() <= max ? text : text.substring(0, max - 1) + "…"; }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
}
