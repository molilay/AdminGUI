package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.ConfirmGUI;
import me.admin.gui.manager.ProtectedPlayersManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ProtectedPlayersGUI extends PaginatedGUI {

    private static final int SLOT_ADD_PLAYER = 46;
    private List<String> protectedNames = new ArrayList<>();

    public ProtectedPlayersGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return "§8≡ Защищённые игроки";
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        protectedNames = plugin.getProtectedPlayersManager().getProtectedNames();
        Collections.sort(protectedNames);

        for (String name : protectedNames) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
            boolean online = op != null && op.isOnline();

            ItemStack head = ItemBuilder.createPlayerHead();
            if (op != null) {
                head = new ItemBuilder(Material.PLAYER_HEAD)
                        .name((online ? "&a" : "&7") + name)
                        .lore(
                                online ? "&aВ сети" : "&cНе в сети",
                                "",
                                "&eНажмите чтобы убрать защиту"
                        )
                        .build();
            } else {
                head = new ItemBuilder(Material.PLAYER_HEAD)
                        .name("&7" + name)
                        .lore("", "&eНажмите чтобы убрать защиту")
                        .build();
            }
            contentItems.add(head);
        }
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            if (slot == SLOT_ADD_PLAYER) {
                SoundUtil.click(viewer);
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer,
                        "§eВведите ник игрока для добавления в защиту:",
                        input -> {
                            String name = input.trim();
                            if (name.isEmpty()) {
                                viewer.sendMessage("§cНик не может быть пустым.");
                                open();
                                return;
                            }
                            new ConfirmGUI(plugin, viewer, "§aДобавить " + name + " в защиту?",
                                () -> {
                                    if (plugin.getProtectedPlayersManager().addPlayer(name)) {
                                        viewer.sendMessage("§8[§cAM§8] §aИгрок §f" + name + " §aдобавлен в защиту.");
                                    } else {
                                        viewer.sendMessage("§cИгрок не найден или уже в защите.");
                                    }
                                    open();
                                },
                                () -> open()
                            ).open();
                        });
                return;
            }
            if (slot == SLOT_MAIN_MENU) {
                SoundUtil.click(viewer);
                openHome();
                return;
            }
            if (slot == SLOT_CLOSE) {
                SoundUtil.click(viewer);
                close();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        SoundUtil.click(viewer);
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < protectedNames.size()) {
            String name = protectedNames.get(index);
            new ConfirmGUI(plugin, viewer, "§cСнять защиту с " + name + "?",
                () -> {
                    if (plugin.getProtectedPlayersManager().removePlayer(name)) {
                        viewer.sendMessage("§8[§cAM§8] §cЗащита с игрока §f" + name + " §cснята.");
                    }
                    refresh();
                },
                () -> refresh()
            ).open();
        }
    }

    @Override
    protected Inventory buildInventory() {
        buildContent();
        Inventory inv = super.buildInventory();

        inv.setItem(SLOT_ADD_PLAYER, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&l+ Добавить игрока")
                .lore("&7Нажмите чтобы добавить", "&7нового защищённого игрока")
                .glowing()
                .build());

        for (int i = 0; i < SIZE; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createFiller());
            }
        }
        return inv;
    }
}
