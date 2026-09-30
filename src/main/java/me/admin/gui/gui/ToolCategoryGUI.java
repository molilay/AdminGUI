package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.CapabilityRegistry;
import me.admin.gui.utils.CapabilityRegistry.Capability;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Permission-aware category page using the same registry as dashboard and commands. */
public final class ToolCategoryGUI extends PaginatedGUI {
    public enum Category { PLAYERS, INVESTIGATIONS, PUNISHMENTS, SECURITY, SYSTEM }
    private final Category category;
    public ToolCategoryGUI(AdvancedModeratorGUI plugin, Player viewer, Category category) { super(plugin, viewer); this.category = category; }
    @Override public String getTitle() { return tr("gui.category." + category.name().toLowerCase() + ".title", "&8" + category.name()); }
    @Override public void buildContent() { contentItems.clear(); }

    @Override protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        switch (category) {
            case PLAYERS -> {
                item(inventory, 11, Capability.PLAYER, Material.LIME_WOOL, "players-online", "&aИгроки онлайн");
                item(inventory, 13, Capability.PLAYER, Material.GRAY_WOOL, "players-offline", "&7Офлайн-игроки");
                item(inventory, 15, Capability.GROUPS, Material.COMMAND_BLOCK, "groups", "&dГруппы и права");
                item(inventory, 22, Capability.PROTECT, Material.SHIELD, "protected", "&eЗащищённые игроки");
            }
            case INVESTIGATIONS -> {
                item(inventory, 10, Capability.REPORTS, Material.WRITABLE_BOOK, "reports", "&cЖалобы");
                item(inventory, 12, Capability.CASES, Material.LECTERN, "cases", "&6Дела модерации");
                item(inventory, 14, Capability.APPEALS, Material.GOLD_BLOCK, "appeals", "&eАпелляции");
                item(inventory, 16, Capability.AUDIT, Material.BOOK, "audit", "&bЗащищённый аудит");
                item(inventory, 20, Capability.INBOX, Material.HOPPER, "inbox", "&aМои задачи");
                item(inventory, 22, Capability.INVESTIGATION, Material.COMPASS, "select-player", "&aВыбрать игрока");
                item(inventory, 24, Capability.INCIDENT, Material.RECOVERY_COMPASS, "incident", "&bIncident Timeline");
            }
            case PUNISHMENTS -> {
                item(inventory, 10, Capability.BAN, Material.IRON_BARS, "active-bans", "&cАктивные баны");
                item(inventory, 12, Capability.MUTE, Material.PAPER, "active-mutes", "&eАктивные муты");
                item(inventory, 14, Capability.IP_BANS, Material.IRON_TRAPDOOR, "ip-bans", "&4IP-баны");
                item(inventory, 16, Capability.TEMPLATES, Material.ENCHANTED_BOOK, "templates", "&dШаблоны");
                item(inventory, 22, Capability.LOGS, Material.GOLD_NUGGET, "statistics", "&6Статистика");
            }
            case SECURITY -> {
                item(inventory, 10, Capability.ANTIRAID, Material.SHIELD, "antiraid", "&cAntiRaid");
                item(inventory, 12, Capability.AUTOMOD, Material.REPEATER, "automod", "&6AutoMod");
                item(inventory, 14, Capability.AUDIT, Material.BOOK, "audit", "&bАудит");
                item(inventory, 16, Capability.PROTECT, Material.TOTEM_OF_UNDYING, "protected", "&eЗащита игроков");
                item(inventory, 20, Capability.SIMULATOR, Material.COMPARATOR, "simulator", "&bSecurity Simulator");
                item(inventory, 22, Capability.DOCTOR, Material.COMPARATOR, "doctor", "&dДиагностика");
                item(inventory, 24, Capability.INBOX, Material.HOPPER, "inbox", "&aМои задачи");
            }
            case SYSTEM -> {
                item(inventory, 11, Capability.DOCTOR, Material.COMPARATOR, "doctor", "&dDoctor / Health");
                item(inventory, 13, Capability.LOGS, Material.BOOKSHELF, "logs", "&eЛоги наказаний");
                item(inventory, 15, Capability.AUDIT, Material.EMERALD, "audit-integrity", "&aЦелостность аудита");
                item(inventory, 22, Capability.ADMIN, Material.CHEST, "all-tools", "&6Все инструменты");
            }
        }
        inventory.setItem(48, new ItemBuilder(Material.ARROW).name(tr("common.actions.home", "&e⌂ Dashboard")).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }
    private void item(Inventory inventory, int slot, Capability capability, Material material, String key, String fallback) {
        if (CapabilityRegistry.allows(viewer, capability)) inventory.setItem(slot,
                new ItemBuilder(material).name(tr("gui.tools." + key, fallback)).build());
    }

    @Override public void onClick(int slot) {
        SoundUtil.click(viewer);
        if (slot == 48) { openHome(); return; }
        if (slot == SLOT_CLOSE) { close(); return; }
        switch (category) {
            case PLAYERS -> {
                if (slot == 11 && allowed(Capability.PLAYER)) new PlayerListGUI(plugin, viewer, true).open();
                else if (slot == 13 && allowed(Capability.PLAYER)) new PlayerListGUI(plugin, viewer, false).open();
                else if (slot == 15 && allowed(Capability.GROUPS)) new GroupListGUI(plugin, viewer).open();
                else if (slot == 22 && allowed(Capability.PROTECT)) new ProtectedPlayersGUI(plugin, viewer).open();
            }
            case INVESTIGATIONS -> {
                if (slot == 10 && allowed(Capability.REPORTS)) new ReportsGUI(plugin, viewer).open();
                else if (slot == 12 && allowed(Capability.CASES)) new CasesGUI(plugin, viewer).open();
                else if (slot == 14 && allowed(Capability.APPEALS)) new AppealsGUI(plugin, viewer).open();
                else if (slot == 16 && allowed(Capability.AUDIT)) new AuditGUI(plugin, viewer).open();
                else if (slot == 20 && allowed(Capability.INBOX)) new TriageInboxGUI(plugin, viewer).open();
                else if (slot == 22 && allowed(Capability.INVESTIGATION)) new PlayerListGUI(plugin, viewer, true).open();
                else if (slot == 24 && allowed(Capability.INCIDENT)) promptIncident();
            }
            case PUNISHMENTS -> {
                if (slot == 10 && allowed(Capability.BAN)) new ActiveBansGUI(plugin, viewer).open();
                else if (slot == 12 && allowed(Capability.MUTE)) new ActiveMutesGUI(plugin, viewer).open();
                else if (slot == 14 && allowed(Capability.IP_BANS)) new ActiveIpBansGUI(plugin, viewer).open();
                else if (slot == 16 && allowed(Capability.TEMPLATES)) new me.admin.gui.manager.PunishmentTemplateManager.PunishmentTemplateGUI(plugin, viewer).open();
                else if (slot == 22 && allowed(Capability.LOGS)) new PunishmentStatsGUI(plugin, viewer).open();
            }
            case SECURITY -> {
                if (slot == 10 && allowed(Capability.ANTIRAID)) new AntiRaidGUI(plugin, viewer).open();
                else if (slot == 12 && allowed(Capability.AUTOMOD)) new AutoModGUI(plugin, viewer).open();
                else if (slot == 14 && allowed(Capability.AUDIT)) new AuditGUI(plugin, viewer).open();
                else if (slot == 16 && allowed(Capability.PROTECT)) new ProtectedPlayersGUI(plugin, viewer).open();
                else if (slot == 20 && allowed(Capability.SIMULATOR)) new SecuritySimulatorGUI(plugin, viewer).open();
                else if (slot == 22 && allowed(Capability.DOCTOR)) new DoctorGUI(plugin, viewer).open();
                else if (slot == 24 && allowed(Capability.INBOX)) new TriageInboxGUI(plugin, viewer).open();
            }
            case SYSTEM -> {
                if (slot == 11 && allowed(Capability.DOCTOR)) new DoctorGUI(plugin, viewer).open();
                else if (slot == 13 && allowed(Capability.LOGS)) new LogsGUI(plugin, viewer).open();
                else if (slot == 15 && allowed(Capability.AUDIT)) new AuditGUI(plugin, viewer).open();
                else if (slot == 22 && allowed(Capability.ADMIN)) new MainMenu(plugin, viewer).open();
            }
        }
    }
    private boolean allowed(Capability capability) {
        if (CapabilityRegistry.allows(viewer, capability)) return true;
        viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return false;
    }
    private void promptIncident() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, tr("gui.incident.prompt-player", "§eВведите ник игрока для Incident Timeline:"), name -> {
            if (!allowed(Capability.INCIDENT)) return;
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name.trim());
            if (target == null) viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            else new IncidentTimelineGUI(plugin, viewer, target).open();
        });
    }
    private String tr(String key, String fallback) { return plugin.getLocalizationManager().get(key, fallback); }
}
