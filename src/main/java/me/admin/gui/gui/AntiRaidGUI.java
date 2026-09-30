package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AntiRaidManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public final class AntiRaidGUI extends PaginatedGUI {
    private List<AntiRaidManager.RaidIncident> incidents = List.of();

    public AntiRaidGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }

    @Override public void open() {
        if (!viewer.hasPermission("amgui.antiraid")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
    }
    @Override public String getTitle() {
        return plugin.getLocalizationManager().get("gui.antiraid.title", "&8AntiRaid — защита сервера");
    }
    @Override public void buildContent() { incidents = plugin.getAntiRaidManager().getIncidents(); }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemBuilder.createFiller());
        AntiRaidManager manager = plugin.getAntiRaidManager();
        boolean lockdown = manager.isLockdown();
        var snapshot = manager.currentSnapshot();
        inventory.setItem(4, new ItemBuilder(lockdown ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK)
                .name(lockdown ? "&cЗащитный режим активен" : "&aСервер работает нормально")
                .lore("&7Уникальных входов: &f" + snapshot.uniqueJoins(),
                        "&7Непроверенных: &f" + snapshot.untrustedJoins(),
                        "&7Новых: &f" + snapshot.newPlayers(),
                        "&7Крупнейшая сеть: &f" + snapshot.largestSubnet(),
                        snapshot.ignoredAddresses() > 0 ? "&8IP без адреса не учтены: " + snapshot.ignoredAddresses() : "",
                        lockdown ? "&7Осталось: &f" + TimeUtils.formatDuration(manager.remainingSeconds())
                                : manager.isAlertOnly() ? "&eРежим: только уведомления" : "&7Lockdown выключен")
                .glowing(lockdown).build());
        inventory.setItem(20, new ItemBuilder(lockdown ? Material.LEVER : Material.SHIELD)
                .name(lockdown ? "&aВыключить lockdown" : "&cВключить lockdown на 3 минуты")
                .lore("&7Действие требует подтверждения").build());
        inventory.setItem(22, new ItemBuilder(Material.COMPARATOR).name("&eПороговые значения")
                .lore("&7Окно: &f" + plugin.getConfig().getLong("antiraid.window-seconds", 20) + "с",
                        "&7Уникальные входы: &f" + plugin.getConfig().getInt("antiraid.joins-threshold", 15),
                        "&7Новые игроки: &f" + plugin.getConfig().getInt("antiraid.new-player-threshold", 10),
                        "&7Одна сеть (/24 или /64): &f" + plugin.getConfig().getInt("antiraid.same-ip-threshold", 8),
                        "&7Cooldown: &f" + plugin.getConfig().getLong("antiraid.trigger-cooldown-seconds", 120) + "с",
                        "", "&7Повторный вход UUID не увеличивает счётчик").build());
        inventory.setItem(24, new ItemBuilder(Material.OAK_DOOR).name("&6Режим ограничений")
                .lore("&7Блокировка чата: " + (plugin.getConfig().getBoolean("antiraid.block-chat", true) ? "&aда" : "&7нет"),
                        "&7Блок новых входов: " + (plugin.getConfig().getBoolean("antiraid.block-new-joins", false) ? "&cда" : "&aнет"),
                        "&7Автоматические баны: &aникогда",
                        "&7Автодействие: " + (manager.isAlertOnly() ? "&eтолько уведомление" : "&clockdown")).build());

        int max = Math.min(9, incidents.size());
        for (int i = 0; i < max; i++) {
            AntiRaidManager.RaidIncident incident = incidents.get(i);
            inventory.setItem(27 + i, new ItemBuilder(Material.PAPER).name("&c" + incident.trigger())
                    .lore("&7Когда: &f" + TimeUtils.formatLogTime(incident.timestamp()),
                            "&7Кто/система: &f" + incident.actor(), "&7Уникальных: &f" + incident.joins(),
                            "&7Новых: &f" + incident.newPlayers(), "&7Одна сеть: &f" + incident.sameIp()).build());
        }
        inventory.setItem(SLOT_MAIN_MENU, new ItemBuilder(Material.NETHER_STAR).name("&c« В главное меню").build());
        inventory.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inventory;
    }

    @Override
    public void onClick(int slot) {
        if (slot == 20) {
            if (!viewer.hasPermission("amgui.antiraid.toggle")) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            boolean active = plugin.getAntiRaidManager().isLockdown();
            new ConfirmGUI(plugin, viewer, active ? "§aВыключить AntiRaid lockdown?" : "§cВключить lockdown на 3 минуты?", () -> {
                if (!viewer.hasPermission("amgui.antiraid.toggle")) {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return;
                }
                if (active) plugin.getAntiRaidManager().disable(viewer);
                else plugin.getAntiRaidManager().enableManual(viewer, 180);
                new AntiRaidGUI(plugin, viewer).open();
            }).open();
        } else if (slot == SLOT_MAIN_MENU) openHome();
        else if (slot == SLOT_CLOSE) close();
    }
}
