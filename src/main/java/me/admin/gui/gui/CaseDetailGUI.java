package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.ModerationCaseManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detailed case timeline with explicit, confirmed workflow actions. */
public final class CaseDetailGUI extends PaginatedGUI {

    private static final int EVENT_START = 9;
    private static final int EVENTS_PER_PAGE = 36;
    private final int caseId;
    private ModerationCaseManager.ModerationCase moderationCase;
    private List<ModerationCaseManager.CaseEvent> events = List.of();
    private volatile boolean exporting;
    private volatile String exportError;

    public CaseDetailGUI(AdvancedModeratorGUI plugin, org.bukkit.entity.Player viewer, int caseId) {
        super(plugin, viewer);
        this.caseId = caseId;
    }

    @Override public String getTitle() { return plugin.getLocalizationManager().format("gui.case-detail.title",
            "&8Дело модерации #%id%", "id", Integer.toString(caseId)); }

    @Override
    public void buildContent() {
        moderationCase = plugin.getModerationCaseManager().get(caseId);
        if (moderationCase == null) { events = List.of(); return; }
        List<ModerationCaseManager.CaseEvent> reversed = new ArrayList<>(moderationCase.events());
        Collections.reverse(reversed);
        events = List.copyOf(reversed);
        int pages = Math.max(1, (int) Math.ceil(events.size() / (double) EVENTS_PER_PAGE));
        if (page >= pages) page = pages - 1;
    }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.cases")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        buildContent();
        if (moderationCase == null) {
            viewer.sendMessage("§cДело #" + caseId + " не найдено.");
            new CasesGUI(plugin, viewer).open();
            return;
        }
        super.open();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        if (moderationCase == null) return inventory;

        inventory.setItem(4, new ItemBuilder(priorityMaterial(moderationCase.priority()))
                .name("&6#" + moderationCase.id() + " &f" + moderationCase.targetName())
                .lore("&7" + moderationCase.title(), "", "&7Статус: " + statusColor(moderationCase.status()) + plugin.getLocalizationManager().enumLabel("case-status", moderationCase.status()),
                        "&7Приоритет: " + priorityColor(moderationCase.priority()) + plugin.getLocalizationManager().enumLabel("case-priority", moderationCase.priority()),
                        "&7Создал: &f" + moderationCase.createdBy(),
                        "&7Ответственный: &f" + display(moderationCase.assignedTo()),
                        "&7Обновлено: &f" + TimeUtils.formatLogTime(moderationCase.updatedAt()),
                        "&7Дедлайн: " + deadlineText(),
                        "&7Жалоб: &f" + moderationCase.reportIds().size() + " &8| &7улик: &f" + moderationCase.evidenceIds().size()).glowing().build());
        if (viewer.hasPermission("amgui.incident.view")) inventory.setItem(5, new ItemBuilder(Material.RECOVERY_COMPASS)
                .name("&bIncident Timeline").lore("&7Дела, чат, улики, аудит и снапшоты", "&7Без чтения базы данных").build());
        if (viewer.hasPermission("amgui.cases.edit")) inventory.setItem(6, new ItemBuilder(moderationCase.dueAt() > 0 && moderationCase.dueAt() < System.currentTimeMillis()
                ? Material.REDSTONE_BLOCK : Material.CLOCK).name("&eИзменить дедлайн")
                .lore("&7Сейчас: " + deadlineText(), "", "&eКлик — ввести срок: 6ч, 1д, 7д").build());
        if (viewer.hasPermission("amgui.cases.export") && viewer.hasPermission("amgui.viewip")) {
            Material exportMaterial = exporting ? Material.CLOCK : exportError == null ? Material.BUNDLE : Material.BARRIER;
            ItemBuilder exportItem = new ItemBuilder(exportMaterial)
                    .name(exporting ? "&eФормирование экспорта..." : exportError == null ? "&aЭкспорт дела" : "&cОшибка экспорта")
                    .lore("&7JSON: дело, жалобы, улики и аудит", "&7В комплекте SHA-256 manifest", "&cДанные базы не используются");
            if (exportError != null) exportItem.lore("", "&7" + trim(exportError, 80), "&eКлик — повторить");
            inventory.setItem(7, exportItem.glowing(exporting).build());
        }
        if ((moderationCase.status() == ModerationCaseManager.Status.OPEN
                || moderationCase.status() == ModerationCaseManager.Status.INVESTIGATING)
                && viewer.hasPermission("amgui.cases.edit")) {
            inventory.setItem(8, new ItemBuilder(Material.BARRIER).name("&cОтклонить дело")
                    .lore("&7Требует подтверждения").build());
        }

        int start = page * EVENTS_PER_PAGE;
        int end = Math.min(start + EVENTS_PER_PAGE, events.size());
        for (int index = start; index < end; index++) {
            ModerationCaseManager.CaseEvent event = events.get(index);
            inventory.setItem(EVENT_START + index - start, eventItem(event));
        }

        if (page > 0) inventory.setItem(45, ItemBuilder.createPreviousButton());
        inventory.setItem(46, new ItemBuilder(Material.PLAYER_HEAD).name("&bОткрыть карточку игрока").build());
        if (viewer.hasPermission("amgui.cases.assign")) inventory.setItem(47, new ItemBuilder(Material.NAME_TAG).name("&aНазначить себя")
                .lore("&7Сейчас: &f" + display(moderationCase.assignedTo())).build());
        if (viewer.hasPermission("amgui.cases.edit")) inventory.setItem(48, new ItemBuilder(priorityMaterial(moderationCase.priority())).name("&eИзменить приоритет")
                .lore("&7Сейчас: " + priorityColor(moderationCase.priority()) + plugin.getLocalizationManager().enumLabel("case-priority", moderationCase.priority()), "&7Клик — следующий уровень").build());
        int totalPages = Math.max(1, (int) Math.ceil(events.size() / (double) EVENTS_PER_PAGE));
        inventory.setItem(49, ItemBuilder.createPageInfo(page, totalPages));
        if (viewer.hasPermission("amgui.cases.edit")) inventory.setItem(50, new ItemBuilder(Material.OAK_SIGN).name("&aДобавить заметку").build());
        if (viewer.hasPermission("amgui.cases.edit")) inventory.setItem(51, statusItem());
        inventory.setItem(52, ItemBuilder.createCloseButton());
        if (end < events.size()) inventory.setItem(53, ItemBuilder.createNextButton());
        else inventory.setItem(53, new ItemBuilder(Material.ARROW).name("&e« К списку дел").build());
        return inventory;
    }

    private org.bukkit.inventory.ItemStack eventItem(ModerationCaseManager.CaseEvent event) {
        Material material = switch (event.type()) {
            case "created" -> Material.WRITABLE_BOOK;
            case "status" -> Material.COMPARATOR;
            case "assigned" -> Material.NAME_TAG;
            case "note" -> Material.OAK_SIGN;
            case "evidence" -> Material.FILLED_MAP;
            case "report" -> Material.PAPER;
            case "punishment" -> Material.IRON_BARS;
            case "priority" -> Material.REDSTONE_TORCH;
            default -> Material.BOOK;
        };
        List<String> lore = new ArrayList<>();
        lore.add("&7Когда: &f" + TimeUtils.formatLogTime(event.timestamp()));
        lore.add("&7Кто: &f" + event.actor());
        lore.add("");
        lore.addAll(wrap("&7" + event.details(), 46));
        return new ItemBuilder(material).name("&e" + event.type()).lore(lore).build();
    }

    private org.bukkit.inventory.ItemStack statusItem() {
        Material material = switch (moderationCase.status()) {
            case OPEN -> Material.SPYGLASS;
            case INVESTIGATING -> Material.EMERALD;
            case RESOLVED, REJECTED -> Material.WRITABLE_BOOK;
        };
        String action = switch (moderationCase.status()) {
            case OPEN -> "Начать расследование";
            case INVESTIGATING -> "Закрыть как решённое";
            case RESOLVED, REJECTED -> "Переоткрыть дело";
        };
        return new ItemBuilder(material).name("&6" + action)
                .lore(moderationCase.status() == ModerationCaseManager.Status.OPEN ? "&7Начнётся сразу" : "&7Требует подтверждения").build();
    }

    @Override public void onClick(int slot) { onClick(slot, false, false); }

    @Override
    public void onClick(int slot, boolean shift, boolean right) {
        if (moderationCase == null) return;
        if (slot == 45 && page > 0) { page--; refresh(); return; }
        if (slot == 53) {
            if ((page + 1) * EVENTS_PER_PAGE < events.size()) { page++; refresh(); }
            else if (!plugin.getGuiManager().goBack(viewer)) new CasesGUI(plugin, viewer).open();
            return;
        }
        switch (slot) {
            case 5 -> { if (require("amgui.incident.view")) openIncident(); }
            case 8 -> { if (require("amgui.cases.edit")) confirmStatus(ModerationCaseManager.Status.REJECTED, "Отклонить дело #" + caseId + "?"); }
            case 6 -> { if (require("amgui.cases.edit")) promptDeadline(); }
            case 7 -> { if (require("amgui.cases.export") && require("amgui.viewip") && !exporting) exportCase(); }
            case 46 -> openTarget();
            case 47 -> {
                if (!require("amgui.cases.assign")) return;
                plugin.getModerationCaseManager().assign(caseId, viewer.getName(), viewer.getName());
                viewer.sendMessage("§a✓ Вы назначены ответственным за дело #" + caseId);
                refresh();
            }
            case 48 -> {
                if (!require("amgui.cases.edit")) return;
                ModerationCaseManager.Priority[] values = ModerationCaseManager.Priority.values();
                ModerationCaseManager.Priority next = values[(moderationCase.priority().ordinal() + 1) % values.length];
                plugin.getModerationCaseManager().setPriority(caseId, next, viewer.getName());
                viewer.sendMessage("§a✓ Приоритет дела #" + caseId + ": "
                        + plugin.getLocalizationManager().enumLabel("case-priority", next));
                refresh();
            }
            case 50 -> { if (require("amgui.cases.edit")) promptNote(); }
            case 51 -> { if (require("amgui.cases.edit")) changeStatus(); }
            case 52 -> close();
        }
    }

    private void openTarget() {
        OfflinePlayer target = moderationCase.targetUuid() == null ? Bukkit.getOfflinePlayerIfCached(moderationCase.targetName())
                : Bukkit.getOfflinePlayer(moderationCase.targetUuid());
        if (target == null) viewer.sendMessage("§cИгрок не найден в кэше сервера.");
        else new PlayerCardGUI(plugin, viewer, target).open();
    }

    private void openIncident() {
        OfflinePlayer target = moderationCase.targetUuid() == null ? Bukkit.getOfflinePlayerIfCached(moderationCase.targetName())
                : Bukkit.getOfflinePlayer(moderationCase.targetUuid());
        if (target == null) viewer.sendMessage("§cИгрок не найден в кэше сервера.");
        else new IncidentTimelineGUI(plugin, viewer, target).open();
    }

    private void promptNote() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите заметку для дела #" + caseId + ":", note -> {
            if (!viewer.hasPermission("amgui.cases.edit")) { require("amgui.cases.edit"); new CaseDetailGUI(plugin, viewer, caseId).open(); return; }
            String clean = note.trim();
            if (isCancel(clean)) { new CaseDetailGUI(plugin, viewer, caseId).open(); return; }
            if (clean.isBlank()) viewer.sendMessage("§cЗаметка не может быть пустой.");
            else {
                plugin.getModerationCaseManager().addNote(caseId, viewer.getName(), clean);
                viewer.sendMessage("§a✓ Заметка добавлена.");
            }
            new CaseDetailGUI(plugin, viewer, caseId).open();
        });
    }

    private void promptDeadline() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите срок от текущего момента (6ч, 1д, 7д):", input -> {
            if (!viewer.hasPermission("amgui.cases.edit")) { require("amgui.cases.edit"); new CaseDetailGUI(plugin, viewer, caseId).open(); return; }
            if (isCancel(input.trim())) { new CaseDetailGUI(plugin, viewer, caseId).open(); return; }
            long seconds = TimeUtils.parseDuration(input.trim());
            if (seconds <= 0) viewer.sendMessage("§cНеверный срок.");
            else {
                plugin.getModerationCaseManager().setDeadline(caseId, System.currentTimeMillis() + seconds * 1000L, viewer.getName());
                viewer.sendMessage("§a✓ Дедлайн дела обновлён.");
            }
            new CaseDetailGUI(plugin, viewer, caseId).open();
        });
    }

    private void exportCase() {
        exporting = true;
        exportError = null;
        refresh();
        viewer.sendMessage("§eФормирование защищённого экспорта дела #" + caseId + "...");
        plugin.getCaseExportManager().exportAsync(caseId).whenComplete((result, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) return;
                    exporting = false;
                    if (failure != null) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        exportError = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                        viewer.sendMessage("§cОшибка экспорта: " + cause.getMessage());
                    } else {
                        viewer.sendMessage("§a✓ Экспорт: §f" + result.file().getFileName());
                        viewer.sendMessage("§7SHA-256: §f" + result.sha256());
                        plugin.getAuditManager().record(viewer, "case.export", moderationCase.targetName(), moderationCase.targetUuid(),
                                "case=" + caseId + "; file=" + result.file().getFileName() + "; sha256=" + result.sha256() + "; database=false");
                    }
                    if (plugin.getGuiManager().getOpenGUI(viewer) == this) refresh();
                }));
    }

    private void changeStatus() {
        switch (moderationCase.status()) {
            case OPEN -> {
                plugin.getModerationCaseManager().setStatus(caseId, ModerationCaseManager.Status.INVESTIGATING,
                        viewer.getName(), "Начато через GUI");
                refresh();
            }
            case INVESTIGATING -> confirmStatus(ModerationCaseManager.Status.RESOLVED, "Закрыть дело #" + caseId + "?");
            case RESOLVED, REJECTED -> confirmStatus(ModerationCaseManager.Status.OPEN, "Переоткрыть дело #" + caseId + "?");
        }
    }

    private void confirmStatus(ModerationCaseManager.Status status, String title) {
        new ConfirmGUI(plugin, viewer, "§6" + title, () -> {
            if (!viewer.hasPermission("amgui.cases.edit")) { require("amgui.cases.edit"); return; }
            plugin.getModerationCaseManager().setStatus(caseId, status, viewer.getName(), "Изменено через GUI");
            SoundUtil.success(viewer);
            viewer.sendMessage("§a✓ Дело #" + caseId + ": "
                    + plugin.getLocalizationManager().enumLabel("case-status", status));
            new CaseDetailGUI(plugin, viewer, caseId).open();
        }).open();
    }

    private static Material priorityMaterial(ModerationCaseManager.Priority priority) {
        return switch (priority) {
            case LOW -> Material.LIGHT_BLUE_DYE;
            case NORMAL -> Material.LIME_DYE;
            case HIGH -> Material.ORANGE_DYE;
            case CRITICAL -> Material.RED_DYE;
        };
    }

    private static String priorityColor(ModerationCaseManager.Priority priority) {
        return switch (priority) { case LOW -> "&b"; case NORMAL -> "&a"; case HIGH -> "&6"; case CRITICAL -> "&c"; };
    }

    private static String statusColor(ModerationCaseManager.Status status) {
        return switch (status) { case OPEN -> "&a"; case INVESTIGATING -> "&e"; case RESOLVED -> "&b"; case REJECTED -> "&c"; };
    }

    private static String display(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private static boolean isCancel(String value) { return value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("отмена"); }

    private boolean require(String permission) {
        if (viewer.hasPermission(permission)) return true;
        viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        return false;
    }

    private String deadlineText() {
        if (moderationCase.dueAt() <= 0) return "&7—";
        long remaining = moderationCase.dueAt() - System.currentTimeMillis();
        if (remaining < 0 && (moderationCase.status() == ModerationCaseManager.Status.OPEN
                || moderationCase.status() == ModerationCaseManager.Status.INVESTIGATING)) {
            return "&cПРОСРОЧЕНО на " + TimeUtils.formatDuration(Math.max(1, -remaining / 1000L));
        }
        return "&f" + TimeUtils.formatLogTime(moderationCase.dueAt());
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = value;
        while (remaining.length() > width) {
            int split = remaining.lastIndexOf(' ', width);
            if (split < 8) split = width;
            lines.add(remaining.substring(0, split));
            remaining = "&7" + remaining.substring(split).trim();
        }
        lines.add(remaining);
        return lines;
    }
}
