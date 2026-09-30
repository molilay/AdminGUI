package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.ModerationCaseManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CasesGUI extends PaginatedGUI {

    private List<ModerationCaseManager.ModerationCase> cases = List.of();
    private boolean showAll;
    private boolean overdueOnly;
    private String search = "";
    private final UUID contextTargetUuid;
    private final String contextTargetName;
    private static final Map<UUID, ViewState> STATES = new ConcurrentHashMap<>();
    private record ViewState(boolean showAll, boolean overdueOnly, String search, int page) {}

    public CasesGUI(AdvancedModeratorGUI plugin, org.bukkit.entity.Player viewer) {
        this(plugin, viewer, null);
    }

    public CasesGUI(AdvancedModeratorGUI plugin, org.bukkit.entity.Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        contextTargetUuid = target == null ? null : target.getUniqueId();
        contextTargetName = target == null ? null : (target.getName() == null ? target.getUniqueId().toString() : target.getName());
        ViewState state = target == null ? STATES.get(viewer.getUniqueId()) : null;
        if (state != null) {
            showAll = state.showAll();
            overdueOnly = state.overdueOnly();
            search = state.search();
            page = state.page();
        }
    }

    @Override public String getTitle() {
        return contextTargetName == null ? plugin.getLocalizationManager().get("gui.cases.title", "&8Дела модерации")
                : "&8Дела: " + contextTargetName;
    }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.cases")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        cases = search.isBlank() ? (showAll ? plugin.getModerationCaseManager().getAll() : plugin.getModerationCaseManager().getOpen())
                : plugin.getModerationCaseManager().search(search).stream()
                .filter(item -> showAll || item.status() == ModerationCaseManager.Status.OPEN
                        || item.status() == ModerationCaseManager.Status.INVESTIGATING).toList();
        if (contextTargetName != null) {
            cases = cases.stream().filter(item -> (contextTargetUuid != null && contextTargetUuid.equals(item.targetUuid()))
                    || item.targetName().equalsIgnoreCase(contextTargetName)).toList();
        }
        if (overdueOnly) cases = cases.stream().filter(item -> item.dueAt() > 0 && item.dueAt() < System.currentTimeMillis()
                && (item.status() == ModerationCaseManager.Status.OPEN || item.status() == ModerationCaseManager.Status.INVESTIGATING)).toList();
        for (ModerationCaseManager.ModerationCase item : cases) {
            Material material = switch (item.status()) {
                case OPEN -> Material.WRITABLE_BOOK;
                case INVESTIGATING -> Material.SPYGLASS;
                case RESOLVED -> Material.KNOWLEDGE_BOOK;
                case REJECTED -> Material.BARRIER;
            };
            contentItems.add(new ItemBuilder(material).name("&6#" + item.id() + " &f" + item.targetName())
                    .lore("&7" + item.title(),
                            "&7Статус: &f" + plugin.getLocalizationManager().enumLabel("case-status", item.status()),
                            "&7Приоритет: &f" + plugin.getLocalizationManager().enumLabel("case-priority", item.priority()),
                            "&7Создал: &f" + item.createdBy(),
                            "&7Ответственный: &f" + (item.assignedTo().isBlank() ? "—" : item.assignedTo()),
                            "&7Обновлено: &f" + TimeUtils.formatLogTime(item.updatedAt()),
                            "&7Дедлайн: " + deadline(item),
                            "&7Жалоб: &f" + item.reportIds().size() + " &8| &7улик: &f" + item.evidenceIds().size(),
                            "", "&eЛКМ — открыть карточку дела",
                            viewer.hasPermission("amgui.cases.assign") ? "&bShift+ЛКМ — назначить себя" : "&8Назначение недоступно",
                            viewer.hasPermission("amgui.cases.edit") ? "&aПКМ — добавить заметку" : "&8Редактирование недоступно").build());
        }
        if (cases.isEmpty()) contentItems.add(new ItemBuilder(Material.GRAY_DYE).name("&7Дела не найдены")
                .lore(search.isBlank() ? "&7В текущем фильтре нет дел" : "&7Измените запрос или фильтр").build());
    }

    @Override
    protected org.bukkit.inventory.Inventory buildInventory() {
        var inventory = super.buildInventory();
        inventory.setItem(50, new ItemBuilder(Material.HOPPER)
                .name(showAll ? "&eПоказаны все дела" : "&aТолько активные дела")
                .lore("&7Нажмите для смены фильтра").glowing().build());
        inventory.setItem(47, new ItemBuilder(overdueOnly ? Material.REDSTONE_BLOCK : Material.CLOCK)
                .name(overdueOnly ? "&cТолько просроченные" : "&7Все сроки")
                .lore("&7Просрочено сейчас: &f" + plugin.getModerationCaseManager().getOverdue().size(), "&eКлик — переключить")
                .glowing(overdueOnly).build());
        inventory.setItem(51, new ItemBuilder(Material.COMPASS).name(search.isBlank() ? "&bПоиск дел" : "&aПоиск: &f" + search)
                .lore("&7ID, игрок, заголовок, ответственный,", "&7статус или приоритет", "", "&eКлик — изменить", "&cShift+клик — очистить").build());
        if (contextTargetName != null) inventory.setItem(48, new ItemBuilder(Material.ARROW).name("&e« К расследованию").build());
        return inventory;
    }

    @Override public void onClick(int slot) { onClick(slot, false, false); }
    @Override public void onClick(int slot, boolean shift) { onClick(slot, shift, false); }

    @Override
    public void onClick(int slot, boolean shift, boolean right) {
        if (slot >= 45) {
            if (slot == 48 && contextTargetName != null) {
                if (!plugin.getGuiManager().goBack(viewer)) {
                    OfflinePlayer target = contextTargetUuid == null ? org.bukkit.Bukkit.getOfflinePlayer(contextTargetName)
                            : org.bukkit.Bukkit.getOfflinePlayer(contextTargetUuid);
                    new InvestigationCenterGUI(plugin, viewer, target).open();
                }
                return;
            }
            if (slot == 47) { overdueOnly = !overdueOnly; page = 0; remember(); refresh(); return; }
            if (slot == 50) { showAll = !showAll; page = 0; remember(); refresh(); return; }
            if (slot == 51) {
                if (shift) { search = ""; page = 0; remember(); refresh(); return; }
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eПоиск дела (ID, игрок, текст, статус):", input -> {
                    search = isCancel(input) ? "" : input.trim();
                    page = 0;
                    remember();
                    reopen();
                });
                return;
            }
            handlePaginatedClick(slot);
            remember();
            return;
        }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= cases.size()) return;
        ModerationCaseManager.ModerationCase item = cases.get(index);
        SoundUtil.click(viewer);
        if (shift) {
            if (!viewer.hasPermission("amgui.cases.assign")) { deny(); return; }
            plugin.getModerationCaseManager().assign(item.id(), viewer.getName(), viewer.getName());
            viewer.sendMessage("§a✓ Вы назначены ответственным за дело #" + item.id());
            refresh();
            return;
        }
        if (right) {
            if (!viewer.hasPermission("amgui.cases.edit")) { deny(); return; }
            viewer.closeInventory();
            plugin.getChatInputManager().awaitInput(viewer, "§eВведите заметку для дела #" + item.id() + ":", note -> {
                if (!viewer.hasPermission("amgui.cases.edit")) { deny(); reopen(); return; }
                String clean = note.trim();
                if (!isCancel(clean) && !clean.isBlank()) {
                    plugin.getModerationCaseManager().addNote(item.id(), viewer.getName(), clean);
                    viewer.sendMessage("§a✓ Заметка добавлена.");
                }
                reopen();
            });
            return;
        }
        remember();
        new CaseDetailGUI(plugin, viewer, item.id()).open();
    }

    private void remember() {
        if (contextTargetName == null) STATES.put(viewer.getUniqueId(), new ViewState(showAll, overdueOnly, search, page));
    }

    private void reopen() {
        if (contextTargetName == null) new CasesGUI(plugin, viewer).open();
        else {
            OfflinePlayer target = contextTargetUuid == null ? org.bukkit.Bukkit.getOfflinePlayer(contextTargetName)
                    : org.bukkit.Bukkit.getOfflinePlayer(contextTargetUuid);
            new CasesGUI(plugin, viewer, target).open();
        }
    }

    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }

    public static void clearState(UUID viewerId) { STATES.remove(viewerId); }

    private static boolean isCancel(String value) {
        return value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена");
    }

    private static String deadline(ModerationCaseManager.ModerationCase item) {
        if (item.dueAt() <= 0) return "&7—";
        if ((item.status() == ModerationCaseManager.Status.OPEN || item.status() == ModerationCaseManager.Status.INVESTIGATING)
                && item.dueAt() < System.currentTimeMillis()) return "&cПРОСРОЧЕНО " + TimeUtils.formatLogTime(item.dueAt());
        return "&f" + TimeUtils.formatLogTime(item.dueAt());
    }
}
