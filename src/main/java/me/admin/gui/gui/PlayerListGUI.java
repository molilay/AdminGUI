package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerListGUI extends PaginatedGUI {

    private final boolean online;
    private List<OfflinePlayer> players = new ArrayList<>();
    private static final int MAX_OFFLINE_PLAYERS = 1000;
    private static final int SLOT_SEARCH_PLAYER = 46;

    public PlayerListGUI(AdvancedModeratorGUI plugin, Player viewer, boolean online) {
        super(plugin, viewer);
        this.online = online;
    }

    @Override
    public String getTitle() {
        if (online) return plugin.getConfigManager().getGuiTitle("title-players-online");
        return plugin.getConfigManager().getGuiTitle("title-players-offline");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        players.clear();

        if (online) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p);
                contentItems.add(buildPlayerHead(p));
            }
        } else {
            OfflinePlayer[] all = Bukkit.getOfflinePlayers();
            int limit = Math.min(all.length, MAX_OFFLINE_PLAYERS);
            for (int i = 0; i < limit; i++) {
                players.add(all[i]);
                contentItems.add(buildOfflineHead(all[i]));
            }
        }
    }

    private ItemStack buildPlayerHead(Player player) {
        ItemStack head = plugin.getHeadCacheManager().getHead(player);
        ItemBuilder builder = new ItemBuilder(head);
        builder.lore(
                "&7Кликните для управления",
                "",
                "&a✓ Онлайн"
        );
        return builder.build();
    }

    private ItemStack buildOfflineHead(OfflinePlayer player) {
        ItemStack head = plugin.getHeadCacheManager().getHead(player);
        ItemBuilder builder = new ItemBuilder(head);
        builder.lore(
                "&7Кликните для управления",
                "",
                "&7✗ Оффлайн"
        );
        return builder.build();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = super.buildInventory();
        if (SLOT_SEARCH_PLAYER >= 45 && SLOT_SEARCH_PLAYER < 54) {
            inv.setItem(SLOT_SEARCH_PLAYER, new ItemBuilder(Material.COMPASS)
                    .name("&aПоиск игрока по нику")
                    .lore("&7Нажмите чтобы ввести ник")
                    .build());
        }
        return inv;
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) {
                close();
                return;
            }
            if (slot == SLOT_SEARCH_PLAYER) {
                startSearch();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < players.size()) {
            OfflinePlayer target = players.get(index);
            new PlayerCardGUI(plugin, viewer, target).open();
        }
    }

    public void startSearch() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите ник игрока для поиска:", input -> {
            OfflinePlayer target = findPlayer(input);
            if (target != null) {
                new PlayerCardGUI(plugin, viewer, target).open();
            } else {
                viewer.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                open();
            }
        });
    }

    private OfflinePlayer findPlayer(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        for (OfflinePlayer p : players) {
            String pName = p.getName();
            if (pName != null && pName.equalsIgnoreCase(name)) return p;
        }
        return null;
    }
}
