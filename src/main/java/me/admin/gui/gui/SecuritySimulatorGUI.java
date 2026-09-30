package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.SecuritySimulator;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Dry-run UI. Simulator calls never punish, audit or mutate live AntiRaid/AutoMod state. */
public final class SecuritySimulatorGUI extends PaginatedGUI {
    public SecuritySimulatorGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }
    @Override public String getTitle() { return plugin.getLocalizationManager().get("gui.simulator.title", "&8Security Simulator"); }
    @Override public void open() { if (!viewer.hasPermission("amgui.simulator")) { deny(); return; } super.open(); }
    @Override public void buildContent() { contentItems.clear(); }
    @Override protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        inventory.setItem(11, new ItemBuilder(Material.NAME_TAG).name("&bТест текста AutoMod")
                .lore("&7Проверяет отдельный снимок automod.yml", "&7Без cooldown, истории, наказаний и аудита", "", "&eКлик — ввести текст").build());
        inventory.setItem(15, new ItemBuilder(Material.SHIELD).name("&6Сценарий AntiRaid")
                .lore("&7Формат: входы,новые,одна_подсеть", "&7Пример: 20,12,9", "&7Используется новая локальная signal window", "", "&eКлик — ввести сценарий").build());
        inventory.setItem(22, new ItemBuilder(Material.LIME_DYE).name("&aГарантированный DRY-RUN")
                .lore("&7Не меняет AutoMod/AntiRaid", "&7Не создаёт наказания, очередь или аудит").glowing().build());
        inventory.setItem(48, new ItemBuilder(Material.NETHER_STAR).name("&e⌂ Dashboard").build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }
    @Override public void onClick(int slot) {
        if (slot == 11) promptAutoMod();
        else if (slot == 15) promptRaid();
        else if (slot == 48) new ModeratorDashboardGUI(plugin, viewer).open();
        else if (slot == SLOT_CLOSE) close();
    }
    private void promptAutoMod() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите текст для безопасной симуляции AutoMod:", input -> {
            if (!viewer.hasPermission("amgui.simulator")) { deny(); return; }
            List<SecuritySimulator.AutoModMatch> matches = plugin.getSecuritySimulator().simulateAutoMod(input);
            viewer.sendMessage(matches.isEmpty() ? "§a✓ Совпадений нет. Никаких действий не выполнено." : "§cСовпадений: §f" + matches.size());
            matches.stream().limit(20).forEach(match -> viewer.sendMessage(" §8- §e" + match.ruleId() + " §7/ §f" + match.violation() + " §8→ §c" + match.action()));
            new SecuritySimulatorGUI(plugin, viewer).open();
        });
    }
    private void promptRaid() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите входы,новые,одна_подсеть (например 20,12,9):", input -> {
            if (!viewer.hasPermission("amgui.simulator")) { deny(); return; }
            try {
                String[] values = input.replace(" ", "").split(",", -1);
                if (values.length != 3) throw new IllegalArgumentException();
                int joins = Integer.parseInt(values[0]), fresh = Integer.parseInt(values[1]), subnet = Integer.parseInt(values[2]);
                if (joins < 0 || fresh < 0 || subnet < 0 || fresh > joins || subnet > joins || joins > 500) throw new IllegalArgumentException();
                SecuritySimulator.RaidResult result = plugin.getSecuritySimulator().simulateRaid(joins, fresh, subnet);
                viewer.sendMessage(result.triggered() ? "§cСценарий вызвал бы lockdown: §f" + result.trigger() : "§aСценарий не вызвал бы lockdown.");
                viewer.sendMessage("§7unique=" + result.snapshot().uniqueJoins() + ", new=" + result.snapshot().newPlayers()
                        + ", subnet=" + result.snapshot().largestSubnet() + ". §8DRY-RUN");
            } catch (RuntimeException error) { viewer.sendMessage("§cФормат: входы,новые,одна_подсеть; числа 0..500, части не больше входов."); }
            new SecuritySimulatorGUI(plugin, viewer).open();
        });
    }
    private void deny() { viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission")); }
}
