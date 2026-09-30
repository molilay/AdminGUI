package me.admin.gui;

import me.admin.gui.config.ConfigManager;
import me.admin.gui.config.LocalizationManager;
import me.admin.gui.commands.ModCommand;
import me.admin.gui.commands.StaffChatCommand;
import me.admin.gui.commands.AdminCommand;
import me.admin.gui.database.DatabaseManager;
import me.admin.gui.listeners.*;
import me.admin.gui.manager.*;
import me.admin.gui.gui.GUIManager;
import me.admin.gui.gui.PaginatedGUI;
import me.admin.gui.integration.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class AdvancedModeratorGUI extends JavaPlugin {

    private static AdvancedModeratorGUI instance;
    private ConfigManager configManager;
    private LocalizationManager localizationManager;
    private DatabaseManager databaseManager;
    private AuditManager auditManager;
    private ModerationCaseManager moderationCaseManager;
    private PunishmentSecurityManager punishmentSecurityManager;
    private FreezeManager freezeManager;
    private HeadCacheManager headCacheManager;
    private LuckPermsIntegration luckPermsIntegration;
    private GUIManager guiManager;
    private ChatInputManager chatInputManager;
    private AltDetector altDetector;
    private WarnManager warnManager;
    private MuteManager muteManager;
    private InventoryRollbackManager inventoryRollbackManager;
    private PlayerInventoryCache playerInventoryCache;
    private StaffChatManager staffChatManager;
    private PlayerNoteManager playerNoteManager;
    private VaultIntegration vaultIntegration;
    private DiscordWebhook discordWebhook;
    private TpsTracker tpsTracker;
    private PlaceholderAPIHook placeholderAPIHook;
    private AuthMeIntegration authMeIntegration;
    private ViaVersionIntegration viaVersionIntegration;
    private InvestigationManager investigationManager;
    private ProtectedPlayersManager protectedPlayersManager;
    private AutoModManager autoModManager;
    private VanishManager vanishManager;
    private FlagManager flagManager;
    private ChatHistoryManager chatHistoryManager;
    private ReportManager reportManager;
    private AppealManager appealManager;
    private CoreProtectIntegration coreProtectIntegration;
    private StaffModeManager staffModeManager;
    private TrialModerationManager trialModerationManager;
    private StaffActivityManager staffActivityManager;
    private EvidenceManager evidenceManager;
    private PunishmentTemplateManager punishmentTemplateManager;
    private BanWaveManager banWaveManager;
    private AutoEscalationManager autoEscalationManager;
    private AutoBroadcasterManager autoBroadcasterManager;
    private DynmapIntegration dynmapIntegration;
    private WorldGuardIntegration worldGuardIntegration;
    private RuleAcceptManager ruleAcceptManager;
    private StaffKitsManager staffKitsManager;
    private WebPanel webPanel;
    private SimpleVoiceChatIntegration simpleVoiceChatIntegration;
    private GeoIpManager geoIpManager;
    private YamlBackupManager yamlBackupManager;
    private PlayerRiskManager playerRiskManager;
    private ModerationSessionManager moderationSessionManager;
    private AntiRaidManager antiRaidManager;
    private CaseExportManager caseExportManager;
    private DoctorManager doctorManager;
    private SensitiveActionApprovalManager sensitiveActionApprovalManager;
    private WorkflowAssignmentManager workflowAssignmentManager;
    private SecuritySimulator securitySimulator;
    private IncidentManager incidentManager;
    private LifecycleCoordinator lifecycleCoordinator;
    private PluginTaskExecutor taskExecutor;
    private PluginScheduler ownedScheduler;
    private PlayerModerationContextCache playerModerationContextCache;
    private RuntimeMaintenance runtimeMaintenance;

    public record ReloadResult(boolean success, java.util.List<String> errors, java.util.List<String> warnings) {}

    @Override
    public void onEnable() {
        instance = this;

        me.admin.gui.compat.ServerCompat.report(this);

        saveDefaultConfig();
        if (YamlBackupManager.applyPendingRestore(this)) reloadConfig();
        this.localizationManager = new LocalizationManager(this);
        this.configManager = new ConfigManager(this);
        this.luckPermsIntegration = new LuckPermsIntegration();
        if (!this.luckPermsIntegration.setup()) {
            getLogger().severe("LuckPerms API недоступен. Плагин будет отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);
        this.taskExecutor = new PluginTaskExecutor(this);
        this.ownedScheduler = new PluginScheduler(this);
        this.auditManager = new AuditManager(this);
        this.moderationCaseManager = new ModerationCaseManager(this);
        this.freezeManager = new FreezeManager(this);
        this.headCacheManager = new HeadCacheManager(this);
        this.guiManager = new GUIManager();
        this.chatInputManager = new ChatInputManager();
        this.altDetector = new AltDetector(this);
        this.warnManager = new WarnManager(this);
        this.muteManager = new MuteManager(this);
        this.inventoryRollbackManager = new InventoryRollbackManager(this);
        this.playerInventoryCache = new PlayerInventoryCache(this);
        this.staffChatManager = new StaffChatManager(this);
        this.playerNoteManager = new PlayerNoteManager(this);
        this.vaultIntegration = new VaultIntegration(this);
        this.discordWebhook = new DiscordWebhook(this);
        this.tpsTracker = new TpsTracker(this);
        this.investigationManager = new InvestigationManager(this);
        this.protectedPlayersManager = new ProtectedPlayersManager(this);
        this.punishmentSecurityManager = new PunishmentSecurityManager(this);
        this.autoModManager = new AutoModManager(this);
        this.playerModerationContextCache = new PlayerModerationContextCache(this);
        this.vanishManager = new VanishManager(this);
        this.flagManager = new FlagManager(this);
        this.chatHistoryManager = new ChatHistoryManager(this);
        this.reportManager = new ReportManager(this);
        this.appealManager = new AppealManager(this);
        this.coreProtectIntegration = new CoreProtectIntegration(this);
        this.staffModeManager = new StaffModeManager(this);
        this.trialModerationManager = new TrialModerationManager(this);
        this.staffActivityManager = new StaffActivityManager(this);
        this.evidenceManager = new EvidenceManager(this);
        this.punishmentTemplateManager = new PunishmentTemplateManager(this);
        this.banWaveManager = new BanWaveManager(this);
        this.autoEscalationManager = new AutoEscalationManager(this);
        this.autoBroadcasterManager = new AutoBroadcasterManager(this);
        this.dynmapIntegration = new DynmapIntegration(this);
        this.worldGuardIntegration = new WorldGuardIntegration(this);
        this.ruleAcceptManager = new RuleAcceptManager(this);
        this.staffKitsManager = new StaffKitsManager(this);
        this.webPanel = new WebPanel(this);
        this.simpleVoiceChatIntegration = new SimpleVoiceChatIntegration(this);
        this.geoIpManager = new GeoIpManager(this);
        this.yamlBackupManager = new YamlBackupManager(this);
        this.playerRiskManager = new PlayerRiskManager(this);
        this.moderationSessionManager = new ModerationSessionManager(this);
        this.antiRaidManager = new AntiRaidManager(this);
        this.caseExportManager = new CaseExportManager(this);
        this.doctorManager = new DoctorManager(this);
        this.sensitiveActionApprovalManager = new SensitiveActionApprovalManager(this);
        this.workflowAssignmentManager = new WorkflowAssignmentManager(this);
        this.securitySimulator = new SecuritySimulator(this);
        this.incidentManager = new IncidentManager(this);
        this.lifecycleCoordinator = new LifecycleCoordinator(this);
        this.runtimeMaintenance = new RuntimeMaintenance(this);

        freezeManager.loadFrozenPlayers();

        registerIntegrations();
        registerCommands();
        registerListeners();

        lifecycleCoordinator.register("task-runtime", taskExecutor::start, taskExecutor::close);
        lifecycleCoordinator.register("owned-scheduler", this::startOwnedTasks, ownedScheduler::close);
        lifecycleCoordinator.register("player-context-cache", playerModerationContextCache::start, playerModerationContextCache::close);
        lifecycleCoordinator.register("runtime-maintenance", runtimeMaintenance::start, runtimeMaintenance::close);
        lifecycleCoordinator.register("tps-tracker", tpsTracker::start, tpsTracker::stop);
        lifecycleCoordinator.register("protected-players", protectedPlayersManager::start, protectedPlayersManager::stop);
        lifecycleCoordinator.register("auto-escalation", autoEscalationManager::start, autoEscalationManager::shutdown);
        lifecycleCoordinator.registerConditional("auto-broadcaster",
                () -> getConfig().getBoolean("auto-broadcaster.enabled", false),
                autoBroadcasterManager::start, autoBroadcasterManager::stop);
        lifecycleCoordinator.registerConditional("discord-webhook",
                () -> !getConfig().getString("discord.webhook-url", "").isBlank(),
                discordWebhook::start, discordWebhook::shutdown);
        lifecycleCoordinator.registerConditional("web-panel", () -> getConfig().getBoolean("web-panel.enabled", false),
                webPanel::start, webPanel::stop);
        lifecycleCoordinator.registerConditional("yaml-backups", () -> getConfig().getBoolean("backups.enabled", true),
                yamlBackupManager::start, yamlBackupManager::stop);
        try {
            lifecycleCoordinator.startAll();
        } catch (RuntimeException error) {
            getLogger().severe("Фоновые компоненты не запущены: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        me.admin.gui.gui.PaginatedGUI.setMaxItemsPerPage(getConfig().getInt("gui.items-per-page", 45));

        getLogger().info("AdvancedModeratorGUI включён.");
    }

    @Override
    public void onDisable() {
        // Stop background producers before draining their persistence queues.
        if (lifecycleCoordinator != null) lifecycleCoordinator.stopAll();
        if (freezeManager != null) {
            freezeManager.saveFrozenPlayers();
            freezeManager.unfreezeAll();
        }
        if (staffModeManager != null) staffModeManager.disableAll();
        if (altDetector != null) altDetector.flush();
        if (auditManager != null) auditManager.close();
        YamlPersistenceService.closeFor(this);
        if (placeholderAPIHook != null) {
            try { placeholderAPIHook.unregister(); } catch (Exception ignored) {}
        }
        if (databaseManager != null) databaseManager.close();
        instance = null;
        getLogger().info("AdvancedModeratorGUI выключен.");
    }

    private void startOwnedTasks() {
        ownedScheduler.start();
        ownedScheduler.timer("warn-expirations", warnManager::checkExpirations, 6000L, 6000L, true);
        ownedScheduler.timer("mute-expirations", muteManager::checkExpirations, 1200L, 1200L, true);
        ownedScheduler.timer("case-overdue", moderationCaseManager::notifyOverdue, 12000L, 12000L, false);

        if (getConfig().getBoolean("doctor.run-on-start", true)) {
            ownedScheduler.later("doctor-startup", () -> {
                DoctorManager.Report report = doctorManager.run();
                if (report.errors() > 0) getLogger().severe("AMGUI Doctor: ошибок=" + report.errors()
                        + ", предупреждений=" + report.warnings());
                else if (report.warnings() > 0) getLogger().warning("AMGUI Doctor: предупреждений=" + report.warnings());
                else getLogger().info("AMGUI Doctor: все проверки пройдены.");
                report.checks().stream().filter(check -> check.severity() != DoctorManager.Severity.OK)
                        .forEach(check -> getLogger().warning(check.title() + ": " + check.details()));
            }, 40L, true);
        }

        if (getConfig().getBoolean("update-checker.enabled", false)) {
            String currentVersion = getPluginMeta().getVersion();
            String updateUrl = getConfig().getString("update-checker.url", "");
            if (updateUrl == null || updateUrl.isBlank() || updateUrl.contains("your/repo")) {
                getLogger().warning("UpdateChecker включён, но update-checker.url не настроен.");
            } else {
                long interval = Math.max(1200L, getConfig().getLong("update-checker.interval", 72000L));
                ownedScheduler.timer("update-checker", () -> checkForUpdates(currentVersion, updateUrl),
                        100L, interval, true);
            }
        }
    }

    private void checkForUpdates(String currentVersion, String updateUrl) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) java.net.URI.create(updateUrl).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", getName() + "/" + currentVersion);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                getLogger().warning("UpdateChecker: сервер вернул HTTP " + status + ".");
                return;
            }
            String json;
            try (java.io.InputStreamReader stream = new java.io.InputStreamReader(
                    connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
                 java.io.BufferedReader reader = new java.io.BufferedReader(stream)) {
                json = reader.lines().collect(java.util.stream.Collectors.joining());
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                    .matcher(json);
            if (!matcher.find()) {
                getLogger().warning("UpdateChecker: ответ не содержит tag_name.");
                return;
            }
            String latestVersion = matcher.group(1).replaceFirst("^[vV]", "");
            if (!currentVersion.equals(latestVersion)) {
                getLogger().info("[UpdateChecker] Доступна версия " + latestVersion
                        + " (текущая: " + currentVersion + ").");
                taskExecutor.runOnMain(() -> {
                    for (org.bukkit.entity.Player admin : getServer().getOnlinePlayers()) {
                        if (admin.hasPermission("amgui.admin")) {
                            admin.sendMessage("§8[§cAM§8] §aДоступна новая версия: §e" + latestVersion);
                        }
                    }
                });
            }
        } catch (Exception e) {
            getLogger().warning("UpdateChecker: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public ReloadResult reloadPluginConfiguration() {
        java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
        org.bukkit.configuration.file.YamlConfiguration candidate = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            candidate.load(configFile);
        } catch (Exception error) {
            return new ReloadResult(false, java.util.List.of("config.yml не разобран: " + error.getMessage()), java.util.List.of());
        }
        me.admin.gui.config.ConfigurationValidator.Result validation =
                me.admin.gui.config.ConfigurationValidator.validate(candidate);
        if (!validation.valid()) return new ReloadResult(false, validation.errors(), validation.warnings());

        String previous = getConfig().saveToString();
        try {
            configManager.reload();
            applyReloadedComponents();
            return new ReloadResult(true, java.util.List.of(), validation.warnings());
        } catch (Exception error) {
            getLogger().severe("Не удалось применить config reload, выполняется откат: " + error.getMessage());
            try {
                getConfig().loadFromString(previous);
                configManager.adoptCurrent();
                applyReloadedComponents();
            } catch (Exception rollbackError) {
                getLogger().severe("Не удалось откатить конфигурацию: " + rollbackError.getMessage());
            }
            return new ReloadResult(false, java.util.List.of(error.getMessage()), validation.warnings());
        }
    }

    private void applyReloadedComponents() {
        me.admin.gui.utils.SoundUtil.reload();
        localizationManager.reload();
        autoModManager.reload();
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("task-runtime")) {
            throw new IllegalStateException("task-runtime restart failed");
        }
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("owned-scheduler")) {
            throw new IllegalStateException("owned-scheduler restart failed");
        }
        chatHistoryManager.reload();
        headCacheManager.reload();
        playerModerationContextCache.reload();
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("runtime-maintenance")) {
            throw new IllegalStateException("runtime-maintenance restart failed");
        }
        discordWebhook.reload();
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("discord-webhook")) {
            throw new IllegalStateException("discord-webhook restart failed");
        }
        PaginatedGUI.setMaxItemsPerPage(getConfig().getInt("gui.items-per-page", 45));
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("auto-broadcaster")) {
            throw new IllegalStateException("auto-broadcaster restart failed");
        }
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("web-panel")) {
            throw new IllegalStateException("web-panel restart failed");
        }
        geoIpManager.reload();
        if (lifecycleCoordinator != null && !lifecycleCoordinator.restart("yaml-backups")) {
            throw new IllegalStateException("yaml-backups restart failed");
        }
    }

    private void registerIntegrations() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderAPIHook = new PlaceholderAPIHook(this);
            if (placeholderAPIHook.register()) {
                getLogger().info("PlaceholderAPI подключён.");
            }
        }
        this.authMeIntegration = new AuthMeIntegration(this);
        if (authMeIntegration.setup()) getLogger().info("AuthMe подключён.");
        this.viaVersionIntegration = new ViaVersionIntegration();
        if (viaVersionIntegration.setup()) getLogger().info("ViaVersion подключён.");
    }

    private void registerCommands() {
        ModCommand modCmd = new ModCommand(this);
        var modCmdObj = getCommand("mod");
        if (modCmdObj != null) {
            modCmdObj.setExecutor(modCmd);
            modCmdObj.setTabCompleter(modCmd);
        } else getLogger().warning("Команда 'mod' не найдена в plugin.yml");
        var scCmd = getCommand("sc");
        if (scCmd != null) scCmd.setExecutor(new StaffChatCommand(this));
        else getLogger().warning("Команда 'sc' не найдена в plugin.yml");
        var reportCmd = getCommand("report");
        if (reportCmd != null) reportCmd.setExecutor(new me.admin.gui.commands.ReportCommand(this));
        else getLogger().warning("Команда 'report' не найдена в plugin.yml");
        AdminCommand adminCmd = new AdminCommand(this);
        var amguiCmd = getCommand("amgui");
        if (amguiCmd != null) {
            amguiCmd.setExecutor(adminCmd);
            amguiCmd.setTabCompleter(adminCmd);
        } else getLogger().warning("Команда 'amgui' не найдена в plugin.yml");
        var appealCmd = getCommand("appeal");
        if (appealCmd != null) appealCmd.setExecutor(new me.admin.gui.commands.AppealCommand(this));
        else getLogger().warning("Команда 'appeal' не найдена в plugin.yml");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerPunishListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MuteListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new AutoModListener(this), this);
        getServer().getPluginManager().registerEvents(playerModerationContextCache, this);
        getServer().getPluginManager().registerEvents(vanishManager, this);
        getServer().getPluginManager().registerEvents(chatHistoryManager, this);
        getServer().getPluginManager().registerEvents(new InvestigationListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffModeListener(this), this);
        getServer().getPluginManager().registerEvents(new QuickActionListener(this), this);
        getServer().getPluginManager().registerEvents(new LoginAlertListener(this), this);
        getServer().getPluginManager().registerEvents(new RuleAcceptListener(this), this);
    }

    public static AdvancedModeratorGUI getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public LocalizationManager getLocalizationManager() { return localizationManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AuditManager getAuditManager() { return auditManager; }
    public ModerationCaseManager getModerationCaseManager() { return moderationCaseManager; }
    public PunishmentSecurityManager getPunishmentSecurityManager() { return punishmentSecurityManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public HeadCacheManager getHeadCacheManager() { return headCacheManager; }
    public LuckPermsIntegration getLuckPermsIntegration() { return luckPermsIntegration; }
    public GUIManager getGuiManager() { return guiManager; }
    public ChatInputManager getChatInputManager() { return chatInputManager; }
    public AltDetector getAltDetector() { return altDetector; }
    public WarnManager getWarnManager() { return warnManager; }
    public MuteManager getMuteManager() { return muteManager; }
    public InventoryRollbackManager getInventoryRollbackManager() { return inventoryRollbackManager; }
    public PlayerInventoryCache getPlayerInventoryCache() { return playerInventoryCache; }
    public StaffChatManager getStaffChatManager() { return staffChatManager; }
    public PlayerNoteManager getPlayerNoteManager() { return playerNoteManager; }
    public Optional<VaultIntegration> getVaultIntegration() { return Optional.ofNullable(vaultIntegration); }
    public Optional<DiscordWebhook> getDiscordWebhook() { return Optional.ofNullable(discordWebhook); }
    public TpsTracker getTpsTracker() { return tpsTracker; }
    public Optional<PlaceholderAPIHook> getPlaceholderAPIHook() { return Optional.ofNullable(placeholderAPIHook); }
    public Optional<AuthMeIntegration> getAuthMeIntegration() { return Optional.ofNullable(authMeIntegration); }
    public Optional<ViaVersionIntegration> getViaVersionIntegration() { return Optional.ofNullable(viaVersionIntegration); }
    public InvestigationManager getInvestigationManager() { return investigationManager; }
    public ProtectedPlayersManager getProtectedPlayersManager() { return protectedPlayersManager; }
    public AutoModManager getAutoModManager() { return autoModManager; }
    public VanishManager getVanishManager() { return vanishManager; }
    public FlagManager getFlagManager() { return flagManager; }
    public ChatHistoryManager getChatHistoryManager() { return chatHistoryManager; }
    public ReportManager getReportManager() { return reportManager; }
    public AppealManager getAppealManager() { return appealManager; }
    public CoreProtectIntegration getCoreProtectIntegration() { return coreProtectIntegration; }
    public StaffModeManager getStaffModeManager() { return staffModeManager; }
    public TrialModerationManager getTrialModerationManager() { return trialModerationManager; }
    public Optional<StaffActivityManager> getStaffActivityManager() { return Optional.ofNullable(staffActivityManager); }
    public EvidenceManager getEvidenceManager() { return evidenceManager; }
    public PunishmentTemplateManager getPunishmentTemplateManager() { return punishmentTemplateManager; }
    public BanWaveManager getBanWaveManager() { return banWaveManager; }
    public AutoEscalationManager getAutoEscalationManager() { return autoEscalationManager; }
    public AutoBroadcasterManager getAutoBroadcasterManager() { return autoBroadcasterManager; }
    public DynmapIntegration getDynmapIntegration() { return dynmapIntegration; }
    public WorldGuardIntegration getWorldGuardIntegration() { return worldGuardIntegration; }
    public RuleAcceptManager getRuleAcceptManager() { return ruleAcceptManager; }
    public StaffKitsManager getStaffKitsManager() { return staffKitsManager; }
    public WebPanel getWebPanel() { return webPanel; }
    public SimpleVoiceChatIntegration getSimpleVoiceChatIntegration() { return simpleVoiceChatIntegration; }
    public GeoIpManager getGeoIpManager() { return geoIpManager; }
    public YamlBackupManager getYamlBackupManager() { return yamlBackupManager; }
    public PlayerRiskManager getPlayerRiskManager() { return playerRiskManager; }
    public ModerationSessionManager getModerationSessionManager() { return moderationSessionManager; }
    public AntiRaidManager getAntiRaidManager() { return antiRaidManager; }
    public CaseExportManager getCaseExportManager() { return caseExportManager; }
    public DoctorManager getDoctorManager() { return doctorManager; }
    public SensitiveActionApprovalManager getSensitiveActionApprovalManager() { return sensitiveActionApprovalManager; }
    public WorkflowAssignmentManager getWorkflowAssignmentManager() { return workflowAssignmentManager; }
    public SecuritySimulator getSecuritySimulator() { return securitySimulator; }
    public IncidentManager getIncidentManager() { return incidentManager; }
    public LifecycleCoordinator getLifecycleCoordinator() { return lifecycleCoordinator; }
    public PluginTaskExecutor getTaskExecutor() { return taskExecutor; }
    public PluginScheduler getOwnedScheduler() { return ownedScheduler; }
    public PlayerModerationContextCache getPlayerModerationContextCache() { return playerModerationContextCache; }
}
