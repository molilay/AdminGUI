package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.ModerationCaseManager;
import me.admin.gui.manager.PlayerRiskManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Single entry point for a moderator's player investigation. */
public final class InvestigationCenterGUI extends PaginatedGUI {

    private final OfflinePlayer target;

    public InvestigationCenterGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override public String getTitle() { return plugin.getLocalizationManager().format("gui.investigation.title",
            "&8Центр расследования: %player%", "player", displayName()); }
    @Override public void buildContent() {}

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.investigation.center")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
        plugin.getAuditManager().record(viewer, "investigation.center-view", displayName(), target.getUniqueId(), "");
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        PlayerRiskManager.RiskProfile risk = plugin.getPlayerRiskManager().calculate(target);
        List<ModerationCaseManager.ModerationCase> openCases = plugin.getModerationCaseManager().getOpen().stream()
                .filter(value -> target.getUniqueId().equals(value.targetUuid()) || value.targetName().equalsIgnoreCase(displayName())).toList();
        long reports = plugin.getReportManager().getActive().stream().filter(value -> value.target().equalsIgnoreCase(displayName())).count();
        int evidence = plugin.getEvidenceManager().getEvidenceFor(target.getUniqueId()).size();
        int warns = plugin.getWarnManager().getWarnCount(target.getUniqueId());
        int alts = plugin.getAltDetector().findRelatedAccounts(target.getUniqueId()).size();
        int ips = plugin.getAltDetector().getIpData(target.getUniqueId()).size();

        inventory.setItem(4, new ItemBuilder(plugin.getHeadCacheManager().getHead(target)).name("&e" + displayName())
                .lore("&7UUID: &f" + target.getUniqueId(), "&7Статус: " + (target.isOnline() ? "&aонлайн" : "&7оффлайн"),
                        "", "&eКлик — обычная карточка игрока").build());
        List<String> riskLore = new ArrayList<>();
        riskLore.add("&7Оценка: " + riskColor(risk.score()) + risk.score() + "/100 &8(" + risk.level() + ")");
        riskLore.add("&8Это подсказка, не доказательство нарушения.");
        riskLore.add("");
        for (PlayerRiskManager.RiskFactor factor : risk.factors()) {
            riskLore.add((factor.points() >= 0 ? "&c+" : "&a") + factor.points() + " &7" + factor.name() + ": &f" + factor.details());
        }
        inventory.setItem(10, new ItemBuilder(riskMaterial(risk.score())).name("&6Профиль риска").lore(riskLore)
                .glowing(risk.score() >= 45).build());
        setIf(inventory, 11, "amgui.viewip", new ItemBuilder(Material.COMPASS).name("&bIP и GeoIP")
                .lore("&7Адресов: &f" + ips, "&7История местоположений и провайдеров", "", "&eКлик — открыть IP-историю").build());
        setIf(inventory, 12, "amgui.alts", new ItemBuilder(Material.LEAD).name("&dСвязи аккаунтов")
                .lore("&7Найдено связанных: &f" + alts, "&7Проверяются все исторические IP", "", "&eКлик — открыть связи").build());
        setIf(inventory, 13, "amgui.report.staff", new ItemBuilder(Material.WRITABLE_BOOK).name("&cЖалобы")
                .lore("&7Активных на игрока: &f" + reports, "&eКлик — общий список жалоб").glowing(reports > 0).build());
        setIf(inventory, 14, "amgui.evidence", new ItemBuilder(Material.FILLED_MAP).name("&5Улики")
                .lore("&7Активных улик: &f" + evidence, "&eКлик — открыть").build());
        setIf(inventory, 15, "amgui.cases", new ItemBuilder(Material.LECTERN).name("&6Дела модерации")
                .lore("&7Открытых: &f" + openCases.size(), openCases.isEmpty() ? "&eКлик — список дел" : "&eКлик — открыть последнее")
                .glowing(!openCases.isEmpty()).build());
        setIf(inventory, 16, "amgui.warn", new ItemBuilder(Material.PAPER).name("&eПредупреждения")
                .lore("&7Активных: &f" + warns, "&eКлик — список").build());

        setIf(inventory, 20, "amgui.notes", new ItemBuilder(Material.OAK_SIGN).name("&6Заметки персонала")
                .lore("&7Записей: &f" + plugin.getPlayerNoteManager().getNotes(target.getUniqueId()).size(), "&eКлик — показать/добавить").build());
        setIf(inventory, 21, "amgui.logs", new ItemBuilder(Material.BOOK).name("&eИстория чата")
                .lore("&7Последние сообщения и безопасно", "&7отфильтрованные команды").build());
        setIf(inventory, 22, "amgui.investigate", new ItemBuilder(Material.SPYGLASS).name("&bРежим слежки")
                .lore("&7Текущий режим расследования: " + (plugin.getInvestigationManager().isInvestigating(viewer) ? "&aВКЛ" : "&7ВЫКЛ"),
                        "&eКлик — наблюдать за игроком").build());

        boolean activeSession = plugin.getModerationSessionManager().isActive(viewer);
        setIf(inventory, 23, "amgui.investigate", new ItemBuilder(activeSession ? Material.REDSTONE_TORCH : Material.CLOCK)
                .name(activeSession ? "&cЗавершить модераторскую сессию" : "&aНачать модераторскую сессию")
                .lore(activeSession ? "&7Цель: &f" + plugin.getModerationSessionManager().activeTarget(viewer)
                                : "&7Действия по игроку попадут", "&7в итоговую YAML-сводку",
                        activeSession ? "&7Время: &f" + TimeUtils.formatDuration(plugin.getModerationSessionManager().activeDuration(viewer) / 1000) : "")
                .glowing(activeSession).build());
        setIf(inventory, 24, "amgui.investigate", new ItemBuilder(Material.ENDER_CHEST).name("&dИстория сессий")
                .lore("&7Завершено по игроку: &f" + plugin.getModerationSessionManager().getCompletedFor(target.getUniqueId()).size()).build());

        inventory.setItem(48, new ItemBuilder(Material.ARROW).name("&e« К карточке игрока").build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }

    @Override
    public void onClick(int slot) {
        SoundUtil.click(viewer);
        switch (slot) {
            case 4 -> new PlayerCardGUI(plugin, viewer, target).open();
            case 48 -> {
                if (!plugin.getGuiManager().goBack(viewer)) new PlayerCardGUI(plugin, viewer, target).open();
            }
            case 11 -> {
                if (viewer.hasPermission("amgui.viewip")) new IpHistoryGUI(plugin, viewer, target).open();
                else deny();
            }
            case 12 -> {
                if (viewer.hasPermission("amgui.alts")) new AltConnectionsGUI(plugin, viewer, target).open();
                else deny();
            }
            case 13 -> {
                if (viewer.hasPermission("amgui.report.staff")) new PlayerReportsGUI(plugin, viewer, target).open(); else deny();
            }
            case 14 -> {
                if (viewer.hasPermission("amgui.evidence")) new me.admin.gui.manager.EvidenceManager.EvidenceGUI(
                        plugin, viewer, target.getUniqueId(), displayName()).open(); else deny();
            }
            case 15 -> openCase();
            case 16 -> {
                if (viewer.hasPermission("amgui.warn")) new WarnListGUI(plugin, viewer, target).open(); else deny();
            }
            case 20 -> showNotes();
            case 21 -> {
                if (viewer.hasPermission("amgui.logs")) new ChatHistoryGUI(plugin, viewer, target).open(); else deny();
            }
            case 22 -> watchTarget();
            case 23 -> toggleSession();
            case 24 -> showSessions();
            case SLOT_CLOSE -> close();
        }
    }

    private void openCase() {
        if (!viewer.hasPermission("amgui.cases")) { deny(); return; }
        new CasesGUI(plugin, viewer, target).open();
    }

    private void showNotes() {
        if (!viewer.hasPermission("amgui.notes")) { deny(); return; }
        List<String> notes = plugin.getPlayerNoteManager().getNotes(target.getUniqueId());
        viewer.closeInventory();
        viewer.sendMessage("§8[§cAM§8] §7Заметки о §f" + displayName() + ":");
        if (notes.isEmpty()) viewer.sendMessage(" §7— нет заметок");
        else notes.stream().limit(20).forEach(note -> viewer.sendMessage(" §8- " + note));
        viewer.sendMessage("§7Можно использовать шаблон: §ftemplate:suspicious §7или §ftemplate:checked");
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите новую заметку или cancel:", input -> {
            String note = input.trim();
            if (note.toLowerCase(java.util.Locale.ROOT).startsWith("template:")) {
                String id = note.substring("template:".length()).trim().replaceAll("[^a-zA-Z0-9_-]", "");
                String template = plugin.getConfig().getString("moderator-note-templates." + id);
                if (template == null) viewer.sendMessage("§cШаблон заметки не найден: " + id);
                else note = template.replace("%player%", displayName());
            }
            if (!note.equalsIgnoreCase("cancel") && !note.equalsIgnoreCase("отмена") && !note.isBlank()
                    && !note.toLowerCase(java.util.Locale.ROOT).startsWith("template:")) {
                plugin.getPlayerNoteManager().addNote(target.getUniqueId(), displayName(), viewer.getName(), note);
                plugin.getAuditManager().record(viewer, "note.add", displayName(), target.getUniqueId(), note);
            }
            new InvestigationCenterGUI(plugin, viewer, target).open();
        });
    }

    private void watchTarget() {
        if (!viewer.hasPermission("amgui.investigate")) { deny(); return; }
        if (!plugin.getInvestigationManager().isInvestigating(viewer)) plugin.getInvestigationManager().toggleInvestigator(viewer);
        plugin.getInvestigationManager().watch(viewer, displayName());
        refresh();
    }

    private void toggleSession() {
        if (!viewer.hasPermission("amgui.investigate")) { deny(); return; }
        if (!plugin.getModerationSessionManager().isActive(viewer)) {
            if (plugin.getModerationSessionManager().start(viewer, target)) viewer.sendMessage("§a✓ Модераторская сессия начата.");
            refresh();
            return;
        }
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите итог расследования:", conclusion -> {
            String clean = conclusion.trim();
            if (clean.isBlank()) clean = "Без итоговой заметки";
            plugin.getModerationSessionManager().finish(viewer, clean);
            viewer.sendMessage("§a✓ Сессия завершена и сохранена в YAML.");
            new InvestigationCenterGUI(plugin, viewer, target).open();
        });
    }

    private void showSessions() {
        viewer.sendMessage("§8[§cAM§8] §7Завершённые сессии по §f" + displayName() + ":");
        var sessions = plugin.getModerationSessionManager().getCompletedFor(target.getUniqueId());
        if (sessions.isEmpty()) viewer.sendMessage(" §7— нет записей");
        sessions.stream().limit(10).forEach(value -> viewer.sendMessage(" §8- §f" + value.moderatorName()
                + " §7" + TimeUtils.formatLogTime(value.endedAt()) + " §8— §f" + value.conclusion()));
    }

    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
    private void setIf(Inventory inventory, int slot, String permission, org.bukkit.inventory.ItemStack item) {
        if (viewer.hasPermission(permission)) inventory.setItem(slot, item);
    }
    private String displayName() { return target.getName() == null ? "?" : target.getName(); }
    private static Material riskMaterial(int score) { return score >= 70 ? Material.RED_DYE : score >= 45 ? Material.ORANGE_DYE : score >= 20 ? Material.YELLOW_DYE : Material.LIME_DYE; }
    private static String riskColor(int score) { return score >= 70 ? "&4" : score >= 45 ? "&c" : score >= 20 ? "&e" : "&a"; }
}
