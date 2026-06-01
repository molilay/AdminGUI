package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ActiveBansGUI extends PaginatedGUI {

    private List<BanEntry> cachedBans;

    public ActiveBansGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-active-bans");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        cachedBans = getBans();
        for (BanEntry ban : cachedBans) {
            String name = ban.target();
            String reason = ban.reason();
            String source = ban.source();
            Date expires = ban.expires();

            ItemBuilder builder = new ItemBuilder(Material.BARRIER)
                    .name("&c" + name)
                    .lore(
                            "&7Причина: &f" + reason,
                            "&7Источник: &f" + source
                    );

            if (expires != null) {
                builder.lore("&7Истекает: &f" + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(expires));
            } else {
                builder.lore("&7Навсегда");
            }

            builder.lore("", "&eЛКМ — разбанить");
            contentItems.add(builder.build());
        }
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (cachedBans == null) cachedBans = getBans();

        if (index >= 0 && index < cachedBans.size()) {
            String name = cachedBans.get(index).target();
            Bukkit.getBanList(BanList.Type.NAME).pardon(name);
            plugin.getDatabaseManager().logPunishment("unban", viewer.getName(), name, "Разбан из меню", -1);
            viewer.sendMessage("§a✓ Игрок " + name + " разбанен.");
            refresh();
        }
    }

    private List<BanEntry> getBans() {
        List<BanEntry> result = new ArrayList<>();
        Bukkit.getBanList(BanList.Type.NAME).getBanEntries().forEach(ban -> {
            result.add(new BanEntry(
                    ban.getTarget(),
                    ban.getReason() != null ? ban.getReason() : "Не указана",
                    ban.getSource() != null ? ban.getSource() : "Console",
                    ban.getExpiration()
            ));
        });
        return result;
    }

    private record BanEntry(String target, String reason, String source, Date expires) {}
}
