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

/** Role-aware landing screen backed by the central capability registry. */
public final class ModeratorDashboardGUI extends PaginatedGUI {
    public ModeratorDashboardGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }
    @Override public String getTitle() { return tr("gui.dashboard.title", "&8Центр модерации"); }
    @Override public void buildContent() { contentItems.clear(); }

    @Override protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        category(inventory, 10, Capability.DASH_PLAYERS, Material.PLAYER_HEAD, "players", "&aИгроки", "&7Онлайн, офлайн и карточки игроков");
        category(inventory, 12, Capability.DASH_INVESTIGATIONS, Material.SPYGLASS, "investigations", "&bРасследования", "&7Жалобы, дела, апелляции и аудит");
        category(inventory, 14, Capability.DASH_PUNISHMENTS, Material.IRON_BARS, "punishments", "&cНаказания", "&7Баны, муты, шаблоны и статистика");
        category(inventory, 16, Capability.DASH_SECURITY, Material.SHIELD, "security", "&6Безопасность", "&7AntiRaid, AutoMod, Simulator и защита");
        if (CapabilityRegistry.allows(viewer, Capability.INBOX)) inventory.setItem(20, new ItemBuilder(Material.HOPPER)
                .name(tr("gui.dashboard.inbox", "&aМои задачи"))
                .lore(tr("gui.dashboard.inbox-lore", "&7Дела, жалобы, апелляции и AutoMod"),
                        tr("gui.dashboard.overdue", "&7Просроченных дел: &f%count%").replace("%count%", Integer.toString(plugin.getModerationCaseManager().getOverdue().size()))).build());
        category(inventory, 22, Capability.DASH_SYSTEM, Material.COMPARATOR, "system", "&dСистема", "&7Doctor и состояние сервисов");
        if (CapabilityRegistry.allows(viewer, Capability.SIMULATOR)) inventory.setItem(24, new ItemBuilder(Material.COMPARATOR)
                .name(tr("gui.dashboard.simulator", "&bSecurity Simulator"))
                .lore(tr("gui.dashboard.simulator-lore", "&7Проверка политик без наказаний и изменений")).build());
        if (CapabilityRegistry.allows(viewer, Capability.ADMIN)) inventory.setItem(31, new ItemBuilder(Material.CHEST)
                .name(tr("gui.dashboard.all-tools", "&eВсе инструменты")).lore(tr("gui.dashboard.all-tools-lore", "&7Открыть расширенное меню")).build());
        inventory.setItem(49, new ItemBuilder(Material.CLOCK).name("&fTPS: &a" + String.format("%.1f", plugin.getTpsTracker().getCurrent()))
                .lore(tr("gui.dashboard.players-online", "&7Игроков: &f%count%").replace("%count%", Integer.toString(Bukkit.getOnlinePlayers().size())),
                        tr("gui.dashboard.tasks", "&7Задач: &f%count%").replace("%count%", Integer.toString(taskCount()))).build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }

    private void category(Inventory inventory, int slot, Capability capability, Material material,
                          String key, String fallbackName, String fallbackLore) {
        if (!CapabilityRegistry.allows(viewer, capability)) return;
        inventory.setItem(slot, new ItemBuilder(material).name(tr("gui.dashboard." + key, fallbackName))
                .lore(tr("gui.dashboard." + key + "-lore", fallbackLore)).build());
    }

    @Override public void onClick(int slot) {
        SoundUtil.click(viewer);
        switch (slot) {
            case 10 -> openIf(ToolCategoryGUI.Category.PLAYERS, Capability.DASH_PLAYERS);
            case 12 -> openIf(ToolCategoryGUI.Category.INVESTIGATIONS, Capability.DASH_INVESTIGATIONS);
            case 14 -> openIf(ToolCategoryGUI.Category.PUNISHMENTS, Capability.DASH_PUNISHMENTS);
            case 16 -> openIf(ToolCategoryGUI.Category.SECURITY, Capability.DASH_SECURITY);
            case 20 -> { if (CapabilityRegistry.allows(viewer, Capability.INBOX)) new TriageInboxGUI(plugin, viewer).open(); }
            case 22 -> openIf(ToolCategoryGUI.Category.SYSTEM, Capability.DASH_SYSTEM);
            case 24 -> { if (CapabilityRegistry.allows(viewer, Capability.SIMULATOR)) new SecuritySimulatorGUI(plugin, viewer).open(); }
            case 31 -> { if (CapabilityRegistry.allows(viewer, Capability.ADMIN)) new MainMenu(plugin, viewer).open(); }
            case SLOT_CLOSE -> close();
        }
    }
    private void openIf(ToolCategoryGUI.Category category, Capability capability) {
        if (CapabilityRegistry.allows(viewer, capability)) new ToolCategoryGUI(plugin, viewer, category).open();
    }
    private int taskCount() { return plugin.getModerationCaseManager().getOpen().size() + plugin.getReportManager().getActiveCount()
            + plugin.getAppealManager().getPendingCount() + plugin.getAutoModManager().getPendingApprovals().size(); }
    private String tr(String key, String fallback) { return plugin.getLocalizationManager().get(key, fallback); }
}
