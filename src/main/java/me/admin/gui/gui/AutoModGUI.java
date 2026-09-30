package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AutoModManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** AutoMod control hub. Rules and approvals live in dedicated paginated screens. */
public final class AutoModGUI extends PaginatedGUI {
    public AutoModGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }
    @Override public String getTitle() { return plugin.getLocalizationManager().get("gui.automod.title", "&8AutoMod"); }
    @Override public void open() { if (!viewer.hasPermission("amgui.automod.view")) { deny(); return; } super.open(); }
    @Override public void buildContent() { contentItems.clear(); }

    @Override protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        AutoModManager manager = plugin.getAutoModManager();
        toggle(inventory, 10, Material.WRITABLE_BOOK, "Фильтрация чата", manager.isCheckChat());
        toggle(inventory, 11, Material.COMMAND_BLOCK, "Проверка команд", manager.isCheckCommands());
        toggle(inventory, 12, Material.OAK_SIGN, "Проверка табличек", manager.isCheckSigns());
        toggle(inventory, 13, Material.BELL, "Уведомления персонала", manager.isNotifyStaff());
        toggle(inventory, 14, Material.SPYGLASS, "Режим наблюдения", manager.isMonitorMode());
        inventory.setItem(20, new ItemBuilder(Material.BOOKSHELF).name("&eПравила: &f" + manager.getRules().size())
                .lore("&7Пагинация, поиск и фильтры", "&eКлик — открыть").build());
        int pending = manager.getPendingApprovals().size();
        if (viewer.hasPermission("amgui.automod.approve")) inventory.setItem(22, new ItemBuilder(pending > 0 ? Material.REDSTONE_TORCH : Material.SHIELD)
                .name("&cПодтверждения: &f" + pending).lore("&7Одобрение/отклонение на отдельном экране", "&eКлик — открыть").glowing(pending > 0).build());
        if (viewer.hasPermission("amgui.simulator")) inventory.setItem(24, new ItemBuilder(Material.COMPARATOR).name("&bSecurity Simulator")
                .lore("&7Без наказаний и изменения состояния", "&eКлик — открыть").build());
        if (viewer.hasPermission("amgui.automod.edit")) inventory.setItem(31, new ItemBuilder(Material.ANVIL).name("&6Перезагрузить automod.yml")
                .lore("&7Требует подтверждения").build());
        inventory.setItem(48, new ItemBuilder(Material.NETHER_STAR).name("&e⌂ Dashboard").build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }

    private void toggle(Inventory inventory, int slot, Material material, String label, boolean enabled) {
        inventory.setItem(slot, new ItemBuilder(enabled ? material : Material.GRAY_DYE).name((enabled ? "&a✓ " : "&c✗ ") + label)
                .lore("&7Состояние: " + (enabled ? "&aВКЛ" : "&cВЫКЛ"), viewer.hasPermission("amgui.automod.edit") ? "&eКлик — переключить" : "&8Только просмотр")
                .glowing(enabled).build());
    }

    @Override public void onClick(int slot) {
        if (slot == 20) { new AutoModRulesGUI(plugin, viewer).open(); return; }
        if (slot == 22 && viewer.hasPermission("amgui.automod.approve")) { new AutoModApprovalsGUI(plugin, viewer).open(); return; }
        if (slot == 24 && viewer.hasPermission("amgui.simulator")) { new SecuritySimulatorGUI(plugin, viewer).open(); return; }
        if (slot == 48) { new ModeratorDashboardGUI(plugin, viewer).open(); return; }
        if (slot == SLOT_CLOSE) { close(); return; }
        if (!viewer.hasPermission("amgui.automod.edit")) { if (slot >= 10 && slot <= 14 || slot == 31) deny(); return; }
        AutoModManager manager = plugin.getAutoModManager();
        switch (slot) {
            case 10 -> confirm("Переключить фильтрацию чата?", () -> manager.setCheckChat(!manager.isCheckChat()));
            case 11 -> confirm("Переключить проверку команд?", () -> manager.setCheckCommands(!manager.isCheckCommands()));
            case 12 -> confirm("Переключить проверку табличек?", () -> manager.setCheckSigns(!manager.isCheckSigns()));
            case 13 -> confirm("Переключить уведомления?", () -> manager.setNotifyStaff(!manager.isNotifyStaff()));
            case 14 -> confirm("Переключить режим наблюдения?", () -> manager.setMonitorMode(!manager.isMonitorMode()));
            case 31 -> confirm("Перезагрузить automod.yml?", manager::reload);
        }
    }

    private void confirm(String message, Runnable mutation) {
        new ConfirmGUI(plugin, viewer, "§6" + message, () -> {
            if (!viewer.hasPermission("amgui.automod.edit")) { deny(); return; }
            mutation.run(); SoundUtil.success(viewer); new AutoModGUI(plugin, viewer).open();
        }, () -> new AutoModGUI(plugin, viewer).open()).open();
    }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
}
