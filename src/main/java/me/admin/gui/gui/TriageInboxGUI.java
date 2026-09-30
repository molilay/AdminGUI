package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AppealManager;
import me.admin.gui.manager.AutoModApprovalQueue;
import me.admin.gui.manager.ModerationCaseManager;
import me.admin.gui.manager.ReportManager;
import me.admin.gui.manager.WorkflowAssignmentManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Unified work queue for cases, reports, appeals and destructive AutoMod approvals. */
public final class TriageInboxGUI extends PaginatedGUI {
    public enum Filter { ALL, MINE, UNASSIGNED, OVERDUE }
    private enum Kind { CASE, REPORT, APPEAL, AUTOMOD }
    private record InboxItem(Kind kind, int id, String target, String summary, long timestamp,
                             String owner, boolean overdue) {
        String key() { return kind.name().toLowerCase(Locale.ROOT) + ":" + id; }
    }

    private Filter filter;
    private List<InboxItem> visible = List.of();

    public TriageInboxGUI(AdvancedModeratorGUI plugin, Player viewer) { this(plugin, viewer, Filter.ALL); }
    public TriageInboxGUI(AdvancedModeratorGUI plugin, Player viewer, Filter filter) {
        super(plugin, viewer);
        this.filter = filter == null ? Filter.ALL : filter;
    }

    @Override public String getTitle() { return plugin.getLocalizationManager().get("gui.inbox.title", "&8Мои задачи"); }

    @Override public void open() {
        if (!viewer.hasPermission("amgui.inbox.view")) { deny(); return; }
        super.open();
    }

    @Override public void buildContent() {
        contentItems.clear();
        long now = System.currentTimeMillis();
        long stale = Math.clamp(plugin.getConfig().getLong("workflow.inbox.stale-hours", 24), 1, 24 * 365) * 3_600_000L;
        List<InboxItem> items = new ArrayList<>();
        if (viewer.hasPermission("amgui.cases")) for (ModerationCaseManager.ModerationCase value : plugin.getModerationCaseManager().getOpen()) {
            items.add(new InboxItem(Kind.CASE, value.id(), value.targetName(), value.title(), value.updatedAt(),
                    value.assignedTo(), value.dueAt() > 0 && value.dueAt() < now));
        }
        if (viewer.hasPermission("amgui.report.staff")) for (ReportManager.ReportEntry value : plugin.getReportManager().getActive()) {
            items.add(new InboxItem(Kind.REPORT, value.id(), value.target(), value.reason(), value.timestamp(),
                    owner("report:" + value.id()), now - value.timestamp() > stale));
        }
        if (viewer.hasPermission("amgui.appeal.staff")) for (AppealManager.AppealEntry value : plugin.getAppealManager().getPending()) {
            items.add(new InboxItem(Kind.APPEAL, value.id(), value.targetName(), value.reason(), value.timestamp(),
                    owner("appeal:" + value.id()), now - value.timestamp() > stale));
        }
        if (viewer.hasPermission("amgui.automod.approve")) for (AutoModApprovalQueue.Request value : plugin.getAutoModManager().getPendingApprovals()) {
            items.add(new InboxItem(Kind.AUTOMOD, value.id(), value.targetName(), value.action() + " / " + value.ruleId(), value.timestamp(),
                    owner("automod:" + value.id()), now - value.timestamp() > stale));
        }
        visible = items.stream().filter(this::included)
                .sorted(Comparator.comparing(InboxItem::overdue).reversed().thenComparing(InboxItem::timestamp)).toList();
        boolean compact = plugin.getWorkflowAssignmentManager().isCompact(viewer.getUniqueId());
        for (InboxItem item : visible) contentItems.add(new ItemBuilder(material(item))
                .name((item.overdue() ? "&c! " : color(item.kind())) + label(item.kind()) + " #" + item.id() + " &f" + item.target())
                .lore(compact
                        ? List.of("&7" + trim(item.summary(), 48), "&7Ответственный: &f" + display(item.owner()))
                        : List.of("&7" + trim(item.summary(), 64), "", "&7Создано: &f" + TimeUtils.formatLogTime(item.timestamp()),
                        "&7Ответственный: &f" + display(item.owner()), item.overdue() ? "&cПросрочено" : "&aВ срок",
                        "", "&eЛКМ — открыть", viewer.hasPermission("amgui.inbox.claim") ? "&bShift+ЛКМ — забрать" : "&8Назначение недоступно"))
                .glowing(item.overdue()).build());
        if (page * MAX_ITEMS_PER_PAGE >= visible.size()) page = 0;
    }

    @Override protected Inventory buildInventory() {
        Inventory inventory = super.buildInventory();
        inventory.setItem(46, new ItemBuilder(Material.HOPPER).name(tr("gui.inbox.filter", "&eФильтр: &f%filter%").replace("%filter%", filterName()))
                .lore("&7Все / мои / неназначенные / просроченные", "&eКлик — следующий фильтр").build());
        boolean compact = plugin.getWorkflowAssignmentManager().isCompact(viewer.getUniqueId());
        inventory.setItem(47, new ItemBuilder(compact ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(tr("gui.inbox.compact", "&bКомпактный режим: %status%").replace("%status%",
                        tr(compact ? "common.status.enabled" : "common.status.disabled", compact ? "&aВКЛ" : "&7ВЫКЛ")))
                .lore("&7Уменьшает подсказки в карточках", "&eКлик — переключить").build());
        inventory.setItem(48, new ItemBuilder(Material.NETHER_STAR).name("&e⌂ Dashboard").build());
        inventory.setItem(50, new ItemBuilder(Material.KNOWLEDGE_BOOK).name(tr("gui.inbox.legend", "&bЛегенда"))
                .lore(tr("gui.inbox.legend-open", "&eЛКМ &7— открыть"), tr("gui.inbox.legend-claim", "&bShift+ЛКМ &7— забрать задачу"),
                        tr("gui.inbox.legend-overdue", "&cСвечение &7— просрочено")).build());
        return inventory;
    }

    @Override public void onClick(int slot) { onClick(slot, false, false); }
    @Override public void onClick(int slot, boolean shift, boolean right) {
        if (slot == 46) { filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length]; page = 0; refresh(); return; }
        if (slot == 47) { plugin.getWorkflowAssignmentManager().toggleCompact(viewer.getUniqueId()); refresh(); return; }
        if (slot == 48) { new ModeratorDashboardGUI(plugin, viewer).open(); return; }
        if (slot >= 45) { handlePaginatedClick(slot); return; }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= visible.size()) return;
        InboxItem item = visible.get(index);
        if (shift) requestClaim(item); else openEntity(item);
    }

    private void requestClaim(InboxItem captured) {
        if (!viewer.hasPermission("amgui.inbox.claim")) { deny(); return; }
        String current = currentOwner(captured);
        boolean takeover = !current.isBlank() && !current.equalsIgnoreCase(viewer.getName());
        if (takeover && !viewer.hasPermission("amgui.inbox.takeover")) { deny(); return; }
        new ConfirmGUI(plugin, viewer, (takeover ? "§cПереназначить задачу с " + current : "§aЗабрать задачу")
                + " на себя?", () -> completeClaim(captured, current, takeover), () -> new TriageInboxGUI(plugin, viewer, filter).open()).open();
    }

    private void completeClaim(InboxItem captured, String expectedOwner, boolean takeover) {
        if (!viewer.hasPermission("amgui.inbox.claim") || takeover && !viewer.hasPermission("amgui.inbox.takeover")) { deny(); return; }
        boolean success;
        String result;
        if (captured.kind() == Kind.CASE) {
            ModerationCaseManager.ClaimResult claim = plugin.getModerationCaseManager().claim(captured.id(), viewer.getName(), expectedOwner, takeover, viewer.getName());
            success = claim == ModerationCaseManager.ClaimResult.CLAIMED || claim == ModerationCaseManager.ClaimResult.ALREADY_OWNED;
            result = claim.name();
        } else {
            WorkflowAssignmentManager.ClaimResult claim = plugin.getWorkflowAssignmentManager().claim(captured.key(), viewer.getName(), expectedOwner, takeover, exists(captured));
            success = claim == WorkflowAssignmentManager.ClaimResult.CLAIMED || claim == WorkflowAssignmentManager.ClaimResult.ALREADY_OWNED;
            result = claim.name();
            if (claim == WorkflowAssignmentManager.ClaimResult.CLAIMED) plugin.getAuditManager().record(viewer,
                    "inbox.claim", captured.target(), null, "entity=" + captured.key() + "; takeover=" + takeover);
        }
        viewer.sendMessage(success ? "§a✓ Задача назначена вам." : "§cКонфликт назначения: " + result + ". Обновите список.");
        new TriageInboxGUI(plugin, viewer, filter).open();
    }

    private void openEntity(InboxItem item) {
        switch (item.kind()) {
            case CASE -> new CaseDetailGUI(plugin, viewer, item.id()).open();
            case REPORT -> new ReportsGUI(plugin, viewer).open();
            case APPEAL -> new AppealsGUI(plugin, viewer).open();
            case AUTOMOD -> new AutoModApprovalsGUI(plugin, viewer).open();
        }
    }

    private boolean included(InboxItem item) { return switch (filter) {
        case ALL -> true; case MINE -> item.owner().equalsIgnoreCase(viewer.getName());
        case UNASSIGNED -> item.owner().isBlank(); case OVERDUE -> item.overdue();
    }; }
    private boolean exists(InboxItem item) { return switch (item.kind()) {
        case CASE -> plugin.getModerationCaseManager().get(item.id()) != null;
        case REPORT -> { ReportManager.ReportEntry value = plugin.getReportManager().get(item.id()); yield value != null && !value.resolved() && !value.archived(); }
        case APPEAL -> { AppealManager.AppealEntry value = plugin.getAppealManager().get(item.id()); yield value != null && value.status() == AppealManager.AppealStatus.PENDING; }
        case AUTOMOD -> plugin.getAutoModManager().getPendingApprovals().stream().anyMatch(value -> value.id() == item.id());
    }; }
    private String currentOwner(InboxItem item) { if (item.kind() != Kind.CASE) return owner(item.key());
        ModerationCaseManager.ModerationCase value = plugin.getModerationCaseManager().get(item.id()); return value == null ? "" : value.assignedTo(); }
    private String owner(String key) { return plugin.getWorkflowAssignmentManager().owner(key); }
    private String filterName() { return switch (filter) { case ALL -> "Все"; case MINE -> "Мои"; case UNASSIGNED -> "Неназначенные"; case OVERDUE -> "Просроченные"; }; }
    private static Material material(InboxItem item) { return switch (item.kind()) { case CASE -> Material.LECTERN; case REPORT -> Material.WRITABLE_BOOK; case APPEAL -> Material.GOLD_BLOCK; case AUTOMOD -> Material.REDSTONE_TORCH; }; }
    private static String label(Kind kind) { return switch (kind) { case CASE -> "Дело"; case REPORT -> "Жалоба"; case APPEAL -> "Апелляция"; case AUTOMOD -> "AutoMod"; }; }
    private static String color(Kind kind) { return switch (kind) { case CASE -> "&6"; case REPORT -> "&c"; case APPEAL -> "&e"; case AUTOMOD -> "&d"; }; }
    private static String display(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String trim(String value, int length) { return value.length() <= length ? value : value.substring(0, length - 1) + "…"; }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
    private String tr(String key, String fallback) { return plugin.getLocalizationManager().get(key, fallback); }
}
