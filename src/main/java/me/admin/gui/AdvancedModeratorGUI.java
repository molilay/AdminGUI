package me.admin.gui;

import me.admin.gui.config.ConfigManager;
import me.admin.gui.commands.ModCommand;
import me.admin.gui.commands.StaffChatCommand;
import me.admin.gui.commands.AdminCommand;
import me.admin.gui.database.DatabaseManager;
import me.admin.gui.listeners.*;
import me.admin.gui.manager.*;
import me.admin.gui.gui.GUIManager;
import me.admin.gui.integration.LuckPermsIntegration;
import me.admin.gui.integration.VaultIntegration;
import me.admin.gui.integration.DiscordWebhook;
import org.bukkit.BanList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class AdvancedModeratorGUI extends JavaPlugin {

    private static AdvancedModeratorGUI instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
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

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        this.databaseManager = new DatabaseManager(this);
        this.freezeManager = new FreezeManager(this);
        this.headCacheManager = new HeadCacheManager();
        this.luckPermsIntegration = new LuckPermsIntegration();
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

        freezeManager.loadFrozenPlayers();
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> warnManager.checkExpirations(), 1200L, 1200L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> muteManager.checkExpirations(), 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> checkExpiredBans(), 6000L, 6000L);

        registerCommands();
        registerListeners();

        getLogger().info("AdvancedModeratorGUI включён.");
    }

    @Override
    public void onDisable() {
        freezeManager.saveFrozenPlayers();
        if (databaseManager != null) databaseManager.close();
        if (freezeManager != null) freezeManager.unfreezeAll();
        getLogger().info("AdvancedModeratorGUI выключен.");
    }

    private void registerCommands() {
        ModCommand modCmd = new ModCommand(this);
        getCommand("mod").setExecutor(modCmd);
        getCommand("mod").setTabCompleter(modCmd);
        getCommand("sc").setExecutor(new StaffChatCommand(this));
        AdminCommand adminCmd = new AdminCommand(this);
        getCommand("amgui").setExecutor(adminCmd);
        getCommand("amgui").setTabCompleter(adminCmd);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerPunishListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MuteListener(this), this);
    }

    private void checkExpiredBans() {
        var banList = getServer().getBanList(BanList.Type.NAME);
        banList.getBanEntries().forEach(entry -> {
            if (entry.getExpiration() != null && entry.getExpiration().before(new java.util.Date())) {
                banList.pardon(entry.getTarget());
            }
        });
    }

    public static AdvancedModeratorGUI getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
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
}
