package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.StaffActivityManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MainMenu extends PaginatedGUI {

    private static final int SLOT_SEARCH_PLAYER = 14;
    private static final int SLOT_PLAYERS_ONLINE = 20;
    private static final int SLOT_PLAYERS_OFFLINE = 22;
    private static final int SLOT_GROUPS = 24;
    private static final int SLOT_LOGS = 31;
    private static final int SLOT_STAFFCHAT = 15;
    private static final int SLOT_TPS = 4;
    private static final int SLOT_BANS = 33;
    private static final int SLOT_INVENTORY_ROLLBACK = 29;
    private static final int SLOT_WARN = 25;
    private static final int SLOT_STATS = 40;
    private static final int SLOT_STAFF_LIST = 41;
    private static final int SLOT_VANISH = 42;
    private static final int SLOT_REPORTS = 43;
    private static final int SLOT_APPEALS = 44;
    private static final int SLOT_STAFF_MODE = 10;
    private static final int SLOT_TEMPLATES = 11;
    private static final int SLOT_BANWAVE = 12;
    private static final int SLOT_STAFF_KITS = 13;
    private static final int SLOT_STAFF_ACTIVITY = 16;
    private static final int SLOT_EVIDENCE = 17;
    private static final int SLOT_ACTIVE_MUTES = 30;
    private static final int SLOT_IP_BANS = 32;
    private static final int SLOT_RULE_ACCEPT = 18;
    private static final int SLOT_WEB_PANEL = 19;
    private static final int SLOT_AUTOMOD = 21;
    private static final int SLOT_PROTECTED = 23;
    private static final int SLOT_CASES = 26;
    private static final int SLOT_AUDIT = 27;
    private static final int SLOT_BACKUP = 50;
    private static final int SLOT_ANTIRAID = 51;
    private static final int SLOT_DOCTOR = 49;

    public MainMenu(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-main");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
    }

    @Override
    public void onClick(int slot) {
        if (isGlassPane(slot)) return;
        SoundUtil.click(viewer);
        switch (slot) {
            case SLOT_PLAYERS_ONLINE -> new PlayerListGUI(plugin, viewer, true).open();
            case SLOT_PLAYERS_OFFLINE -> new PlayerListGUI(plugin, viewer, false).open();
            case SLOT_GROUPS -> {
                if (viewer.hasPermission("amgui.groups")) {
                    new GroupListGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_LOGS -> {
                if (viewer.hasPermission("amgui.logs")) {
                    new LogsGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_STAFFCHAT -> {
                if (viewer.hasPermission("amgui.staffchat")) {
                    plugin.getStaffChatManager().toggle(viewer);
                    open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_BANS -> {
                if (viewer.hasPermission("amgui.ban")) {
                    new ActiveBansGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_ACTIVE_MUTES -> {
                if (viewer.hasPermission("amgui.mute")) {
                    new ActiveMutesGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_IP_BANS -> {
                if (viewer.hasPermission("amgui.ban")) {
                    new ActiveIpBansGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_INVENTORY_ROLLBACK -> {
                if (viewer.hasPermission("amgui.rollback")) {
                    new PlayerListGUI(plugin, viewer, true).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_WARN -> {
                if (viewer.hasPermission("amgui.warn")) {
                    new PlayerListGUI(plugin, viewer, true).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_SEARCH_PLAYER -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите ник (можно частично):", input -> {
                    List<org.bukkit.OfflinePlayer> matches = searchPlayers(input);
                    if (matches.isEmpty()) {
                        viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                        open();
                    } else if (matches.size() == 1) {
                        new PlayerCardGUI(plugin, viewer, matches.get(0)).open();
                    } else {
                        viewer.sendMessage("§eНайдено несколько игроков:");
                        for (org.bukkit.OfflinePlayer p : matches) {
                            viewer.sendMessage(" §7- §f" + p.getName());
                        }
                        open();
                    }
                });
            }
            case SLOT_VANISH -> {
                if (viewer.hasPermission("amgui.vanish")) {
                    plugin.getVanishManager().toggle(viewer);
                    open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_REPORTS -> {
                if (viewer.hasPermission("amgui.report.staff")) {
                    new ReportsGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_APPEALS -> {
                if (viewer.hasPermission("amgui.appeal.staff")) {
                    new AppealsGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_STAFF_MODE -> {
                if (viewer.hasPermission("amgui.staffmode")) {
                    plugin.getStaffModeManager().toggle(viewer);
                    open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_TEMPLATES -> {
                if (viewer.hasPermission("amgui.templates")) {
                    new me.admin.gui.manager.PunishmentTemplateManager.PunishmentTemplateGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_BANWAVE -> {
                if (viewer.hasPermission("amgui.banwave")) {
                    new me.admin.gui.manager.BanWaveManager.BanWaveGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_STAFF_KITS -> {
                if (viewer.hasPermission("amgui.staffkits")) {
                    new me.admin.gui.manager.StaffKitsManager.StaffKitsGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_STAFF_ACTIVITY -> {
                if (viewer.hasPermission("amgui.staffactivity")) {
                    new StaffActivityManager.StaffActivityGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_EVIDENCE -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите ник игрока для просмотра улик:", input -> {
                    String name = input.trim();
                    if (name.isEmpty()) { open(); return; }
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
                    if (target == null || !target.hasPlayedBefore()) {
                        viewer.sendMessage("§cИгрок не найден.");
                        open();
                        return;
                    }
                    new me.admin.gui.manager.EvidenceManager.EvidenceGUI(plugin, viewer, target.getUniqueId(), name).open();
                });
            }
            case SLOT_RULE_ACCEPT -> {
                if (viewer.hasPermission("amgui.admin")) {
                    if (plugin.getRuleAcceptManager().isEnabled()) {
                        plugin.getConfig().set("rule-accept.enabled", false);
                        plugin.saveConfig();
                        viewer.sendMessage("§c✓ Принятие правил отключено.");
                    } else {
                        plugin.getConfig().set("rule-accept.enabled", true);
                        plugin.saveConfig();
                        viewer.sendMessage("§a✓ Принятие правил включено.");
                    }
                    open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_WEB_PANEL -> {
                if (viewer.hasPermission("amgui.admin")) {
                    if (plugin.getWebPanel().isRunning()) {
                        viewer.sendMessage("§eWebPanel API: §f" + plugin.getWebPanel().getPublicAddress());
                        viewer.sendMessage("§7Авторизация: Bearer token из config.yml");
                    } else if (plugin.getWebPanel().isEnabled()) {
                        viewer.sendMessage("§cWebPanel не запущен. Проверьте token и консоль.");
                    } else {
                        viewer.sendMessage("§cWebPanel выключен в config.yml (web-panel.enabled: true).");
                    }
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_AUTOMOD -> {
                if (viewer.hasPermission("amgui.automod.view")) {
                    new AutoModGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_PROTECTED -> {
                if (viewer.hasPermission("amgui.protect")) {
                    new ProtectedPlayersGUI(plugin, viewer).open();
                } else {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                }
            }
            case SLOT_CASES -> {
                if (viewer.hasPermission("amgui.cases")) new CasesGUI(plugin, viewer).open();
                else viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
            case SLOT_AUDIT -> {
                if (viewer.hasPermission("amgui.audit")) new AuditGUI(plugin, viewer).open();
                else viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
            case SLOT_BACKUP -> {
                if (!viewer.hasPermission("amgui.backup.create")) {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return;
                }
                viewer.closeInventory();
                viewer.sendMessage("§eСоздание резервной копии YAML и audit.log...");
                plugin.getYamlBackupManager().createAsync().whenComplete((result, failure) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!viewer.isOnline()) return;
                            if (failure != null) {
                                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                                SoundUtil.error(viewer);
                                viewer.sendMessage("§cОшибка бэкапа: " + cause.getMessage());
                            } else {
                                SoundUtil.success(viewer);
                                viewer.sendMessage("§a✓ Создан §f" + result.file().getFileName() + " §7(" + result.entries() + " файлов)");
                                plugin.getAuditManager().record(viewer, "backup.create", result.file().getFileName().toString(), null,
                                        "entries=" + result.entries() + "; bytes=" + result.bytes() + "; database=false");
                            }
                            new MainMenu(plugin, viewer).open();
                        }));
            }
            case SLOT_ANTIRAID -> {
                if (viewer.hasPermission("amgui.antiraid")) new AntiRaidGUI(plugin, viewer).open();
                else viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
            case SLOT_DOCTOR -> {
                if (viewer.hasPermission("amgui.doctor")) new DoctorGUI(plugin, viewer).open();
                else viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            }
            case SLOT_STATS -> new PunishmentStatsGUI(plugin, viewer).open();
            case SLOT_CLOSE -> close();
        }
    }

    private List<org.bukkit.OfflinePlayer> searchPlayers(String query) {
        String lower = query.toLowerCase();
        List<org.bukkit.OfflinePlayer> result = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName() != null && (p.getName().equalsIgnoreCase(query) || p.getName().toLowerCase().contains(lower))) {
                result.add(p);
            }
        }
        if (result.isEmpty()) {
            for (org.bukkit.OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                if (p.getName() != null && (p.getName().equalsIgnoreCase(query) || p.getName().toLowerCase().contains(lower))) {
                    result.add(p);
                    if (result.size() >= 50) break;
                }
            }
        }
        return result;
    }

    @Override
    public void open() {
        super.open();
    }

    @Override
    protected Inventory buildInventory() {
        int totalOnline = plugin.getServer().getOnlinePlayers().size();
        double[] tps = plugin.getServer().getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);

        var summary = plugin.getDatabaseManager().getPunishmentSummary();
        long todayBans = summary.todayBans();
        long totalBans = summary.totalBans();
        long totalMutes = summary.totalMutes();
        long totalWarns = summary.totalWarns();

        long staffOnline = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("amgui.staffchat"))
                .count();

        int activeBans = me.admin.gui.utils.BanService.activeProfileBanCount();
        int activeMutes = plugin.getMuteManager().getMutedEntries().size();

        Inventory inv = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));

        // ─── Секция 1: Статус сервера (ряд 0-8) ───
        fillRow(inv, 0, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b≡ Сервер");

        inv.setItem(SLOT_TPS, new ItemBuilder(Material.CLOCK)
                .name("&6Статус сервера")
                .lore(
                        "&7TPS: " + (tps[0] > 18.0 ? "&a" : tps[0] > 15.0 ? "&e" : "&c") + String.format("%.1f", tps[0]),
                        "&7Память: &f" + usedMem + "&7/&f" + maxMem + " MB",
                        "&7Игроков: &f" + totalOnline + "&7/&f" + plugin.getServer().getMaxPlayers()
                )
                .build());

        inv.setItem(SLOT_SEARCH_PLAYER, new ItemBuilder(Material.COMPASS)
                .name("&bБыстрый поиск игрока")
                .lore("&7Нажмите и введите ник", "&7(частичное совпадение)")
                .build());

        // ─── Секция 2: Инструменты модератора (ряд 9-17) ───
        fillRow(inv, 1, Material.LIME_STAINED_GLASS_PANE, "§a≡ Инструменты");

        boolean staffMode = plugin.getStaffModeManager().isStaffMode(viewer);
        inv.setItem(SLOT_STAFF_MODE, new ItemBuilder(staffMode ? Material.DIAMOND_CHESTPLATE : Material.LEATHER_CHESTPLATE)
                .name(staffMode ? "&aРежим персонала ✓" : "&7Режим персонала")
                .lore("&7Набор предметов модератора",
                        "&7Авто-vanish, палочка, компас",
                        staffMode ? "&cКликните чтобы выключить" : "&aКликните чтобы включить")
                .glowing(staffMode)
                .build());

        inv.setItem(SLOT_TEMPLATES, new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&dШаблоны наказаний")
                .lore("&7Быстрое наказание по шаблону",
                        "&7(гриферство, читерство, спам...)")
                .build());

        int queueSize = plugin.getBanWaveManager().getQueueSize();
        inv.setItem(SLOT_BANWAVE, new ItemBuilder(Material.TNT)
                .name("&cБан-волна")
                .lore("&7Список на отложенный бан",
                        queueSize > 0 ? "&7В очереди: &c" + queueSize : "&7Нет игроков в очереди")
                .glowing(queueSize > 0)
                .build());

        inv.setItem(SLOT_STAFF_KITS, new ItemBuilder(Material.CHEST)
                .name("&6Наборы персонала")
                .lore("&7Получить набор предметов", "&7для модератора")
                .build());

        inv.setItem(SLOT_STAFFCHAT, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&bStaffChat")
                .lore(
                        plugin.getStaffChatManager().isToggled(viewer) ? "&a✓ Включён" : "&7✗ Выключен",
                        "&7Кликните для переключения",
                        "&7Либо используйте /sc <сообщение>"
                )
                .glowing(plugin.getStaffChatManager().isToggled(viewer))
                .build());

        inv.setItem(SLOT_STAFF_ACTIVITY, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&eАктивность персонала")
                .lore("&7Логи действий персонала", "&7(кто, кого, когда наказал)")
                .build());

        inv.setItem(SLOT_EVIDENCE, new ItemBuilder(Material.FILLED_MAP)
                .name("&5Улики на игрока")
                .lore("&7Прикрепление и просмотр", "&7текстовых улик на игрока")
                .build());

        // ─── Секция 3: Игроки (ряд 18-26) ───
        fillRow(inv, 2, Material.CYAN_STAINED_GLASS_PANE, "§b≡ Игроки");

        inv.setItem(SLOT_PLAYERS_ONLINE, new ItemBuilder(Material.LIME_WOOL)
                .name("&aИгроки онлайн &7(" + totalOnline + ")")
                .lore("&7Кликните для просмотра", "&7онлайн-игроков и управления")
                .glowing()
                .build());

        inv.setItem(SLOT_PLAYERS_OFFLINE, new ItemBuilder(Material.GRAY_WOOL)
                .name("&7Оффлайн игроки")
                .lore("&7Поиск оффлайн-игроков", "&7по нику и управление")
                .build());

        inv.setItem(SLOT_GROUPS, new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&bУправление ролями")
                .lore("&7Создание, редактирование", "&7и назначение групп LuckPerms")
                .build());

        inv.setItem(SLOT_WARN, new ItemBuilder(Material.PAPER)
                .name("&eПредупреждения (Warns)")
                .lore("&7Выдать предупреждение игроку", "&73 варна = авто-бан")
                .build());

        inv.setItem(SLOT_RULE_ACCEPT, new ItemBuilder(rulesEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL)
                .name(rulesEnabled() ? "&aПравила: ВКЛ" : "&cПравила: ВЫКЛ")
                .lore("&7Принудительное принятие правил", "&7при входе на сервер")
                .glowing(rulesEnabled())
                .build());

        boolean amEnabled = plugin.getAutoModManager().isCheckChat();
        if (viewer.hasPermission("amgui.automod.view")) inv.setItem(SLOT_AUTOMOD,
                new ItemBuilder(amEnabled ? Material.COMMAND_BLOCK : Material.REPEATER)
                        .name((amEnabled ? "&a" : "&c") + "AutoMod настройки")
                        .lore("&7Фильтрация чата, спам, флуд",
                                viewer.hasPermission("amgui.automod.edit") ? "&7Настройка правил доступна" : "&8Только просмотр",
                                amEnabled ? "&aФильтрация: ВКЛ" : "&cФильтрация: ВЫКЛ")
                        .glowing(amEnabled).build());

        int protectedCount = plugin.getProtectedPlayersManager().getProtectedUUIDs().size();
        inv.setItem(SLOT_PROTECTED, new ItemBuilder(Material.SHIELD)
                .name("&bЗащищённые игроки")
                .lore("&7Неприкасаемые игроки", "&7(бан/мут/кик — блокируются)",
                        "&7В защите: &f" + protectedCount)
                .glowing(protectedCount > 0)
                .build());

        inv.setItem(SLOT_CASES, new ItemBuilder(Material.LECTERN)
                .name("&6Дела модерации")
                .lore("&7Активных: &f" + plugin.getModerationCaseManager().getOpen().size(),
                        "&7Жалобы, улики, заметки и ответственный")
                .build());

        // ─── Секция 4: Наказания (ряд 27-35) ───
        fillRow(inv, 3, Material.ORANGE_STAINED_GLASS_PANE, "§6≡ Наказания");

        boolean auditValid = plugin.getAuditManager().isIntegrityValid();
        inv.setItem(SLOT_AUDIT, new ItemBuilder(auditValid ? Material.EMERALD : Material.REDSTONE)
                .name("&eЗащищённый аудит")
                .lore(auditValid ? "&a✓ Цепочка SHA-256 цела" : "&c✗ Целостность нарушена",
                        "&7Полная история действий персонала")
                .glowing(auditValid)
                .build());

        inv.setItem(SLOT_LOGS, new ItemBuilder(Material.BOOKSHELF)
                .name("&eЛоги наказаний")
                .lore("&7Просмотр истории", "&7банов, киков и заморозок")
                .build());

        inv.setItem(SLOT_BANS, new ItemBuilder(Material.IRON_BARS)
                .name("&cАктивные баны")
                .lore("&7Список забаненных игроков", "&7и управление ими")
                .build());

        inv.setItem(SLOT_ACTIVE_MUTES, new ItemBuilder(Material.PAPER)
                .name("&eАктивные мьюты")
                .lore("&7Список замьюченных игроков",
                        "&7Активных: &e" + activeMutes)
                .glowing(activeMutes > 0)
                .build());

        if (viewer.hasPermission("amgui.viewip") && viewer.hasPermission("amgui.ban")) {
            inv.setItem(SLOT_IP_BANS, new ItemBuilder(Material.IRON_TRAPDOOR)
                    .name("&cIP-баны").lore("&7Список забаненных IP-адресов", "&7и управление ими").build());
        }

        inv.setItem(SLOT_INVENTORY_ROLLBACK, new ItemBuilder(Material.CHEST)
                .name("&6Откат инвентаря")
                .lore("&7Восстановить инвентарь игрока", "&7из сохранённых снапшотов")
                .build());

        // ─── Секция 5: Статистика (ряд 36-44) ───
        fillRow(inv, 4, Material.PURPLE_STAINED_GLASS_PANE, "§d≡ Статистика");

        inv.setItem(SLOT_STATS, new ItemBuilder(Material.GOLD_NUGGET)
                .name("&6Статистика наказаний")
                .lore(
                        "&7Банов сегодня: &c" + todayBans,
                        "&7Всего банов: &c" + totalBans,
                        "&7Активных банов: &c" + activeBans,
                        "&7Мьютов: &e" + totalMutes,
                        "&7Варнов: &e" + totalWarns,
                        "&7Активных мьютов: &e" + activeMutes,
                        "",
                        "&eЛКМ — подробная статистика"
                )
                .build());

        inv.setItem(SLOT_STAFF_LIST, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&bПерсонал онлайн &7(" + staffOnline + ")")
                .lore("&7Кликните для списка")
                .build());

        boolean vanished = plugin.getVanishManager().isVanished(viewer);
        inv.setItem(SLOT_VANISH, new ItemBuilder(Material.GLASS_BOTTLE)
                .name(vanished ? "&aВы невидимы" : "&7Режим невидимки")
                .lore(vanished ? "&7Кликните чтобы выйти" : "&7Кликните чтобы стать невидимым",
                        "&7Скрывает вас из таба/листа игроков")
                .glowing(vanished)
                .build());

        int activeReports = plugin.getReportManager().getActiveCount();
        inv.setItem(SLOT_REPORTS, new ItemBuilder(Material.BOOK)
                .name("&cЖалобы игроков")
                .lore("&7Принятые жалобы от игроков",
                        activeReports > 0 ? "&7Активных: &c" + activeReports : "&7Новых жалоб нет")
                .glowing(activeReports > 0)
                .build());

        int pendingAppeals = plugin.getAppealManager().getPendingCount();
        inv.setItem(SLOT_APPEALS, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&6Апелляции")
                .lore("&7Апелляции на баны от игроков",
                        pendingAppeals > 0 ? "&7Ожидают: &e" + pendingAppeals : "&7Новых апелляций нет")
                .glowing(pendingAppeals > 0)
                .build());

        inv.setItem(SLOT_WEB_PANEL, new ItemBuilder(Material.REDSTONE_LAMP)
                .name("&6WebPanel")
                .lore("&7Read-only REST API", "&7Адрес: " + plugin.getWebPanel().getPublicAddress(),
                        plugin.getWebPanel().isRunning() ? "&a● Запущен" : "&c● Остановлен")
                .build());

        // Нижняя панель (ряд 5) — навигация
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createFiller());
            }
        }
        if (viewer.hasPermission("amgui.backup.create")) {
            int backupCount = plugin.getYamlBackupManager().listBackups().size();
            inv.setItem(SLOT_BACKUP, new ItemBuilder(Material.BUNDLE).name("&aYAML-бэкап")
                    .lore("&7Конфиги, YAML-данные и audit.log", "&cБаза данных не копируется",
                            "&7Готовых копий: &f" + backupCount, "", "&eКликните для создания").build());
        }
        if (viewer.hasPermission("amgui.antiraid")) {
            boolean lockdown = plugin.getAntiRaidManager().isLockdown();
            inv.setItem(SLOT_ANTIRAID, new ItemBuilder(lockdown ? Material.REDSTONE_BLOCK : Material.SHIELD)
                    .name(lockdown ? "&cAntiRaid: LOCKDOWN" : "&aAntiRaid")
                    .lore("&7Входов в окне: &f" + plugin.getAntiRaidManager().recentJoinCount(),
                            lockdown ? "&7Осталось: &f" + me.admin.gui.utils.TimeUtils.formatDuration(plugin.getAntiRaidManager().remainingSeconds())
                                    : "&7Подозрительной активности нет", "", "&eКлик — управление").glowing(lockdown).build());
        }
        if (viewer.hasPermission("amgui.doctor")) {
            boolean quickError = !plugin.getAuditManager().isIntegrityValid();
            int quickWarnings = plugin.getModerationCaseManager().getOverdue().size()
                    + (plugin.getYamlBackupManager().listBackups().isEmpty() ? 1 : 0);
            inv.setItem(SLOT_DOCTOR, new ItemBuilder(quickError ? Material.REDSTONE : quickWarnings > 0 ? Material.YELLOW_DYE : Material.EMERALD)
                    .name(quickError ? "&cAMGUI Doctor: ошибка" : quickWarnings > 0 ? "&eAMGUI Doctor: нужна проверка" : "&aAMGUI Doctor")
                    .lore("&7Быстрых предупреждений: &f" + quickWarnings, "", "&eКлик — полный отчёт")
                    .glowing(quickError).build());
        }
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inv;
    }

    private boolean rulesEnabled() {
        return plugin.getRuleAcceptManager().isEnabled();
    }

    private void fillRow(Inventory inv, int row, Material pane, String name) {
        int start = row * 9;
        int end = start + 9;
        for (int i = start; i < end; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemBuilder(pane).name(name).build());
            }
        }
    }

    private boolean isGlassPane(int slot) {
        Inventory inv = viewer.getOpenInventory().getTopInventory();
        if (inv == null) return false;
        ItemStack item = inv.getItem(slot);
        if (item == null) return false;
        Material type = item.getType();
        return type.name().contains("STAINED_GLASS_PANE") || type == Material.BLACK_STAINED_GLASS_PANE;
    }
}
