package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.ConfirmGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ActiveBansGUI extends PaginatedGUI {

    private List<me.admin.gui.utils.BanService.ProfileBan> cachedBans;

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
        for (me.admin.gui.utils.BanService.ProfileBan ban : cachedBans) {
            String name = ban.name();
            String reason = ban.reason();
            String source = ban.source();
            Date expires = ban.expiration() == null ? null : Date.from(ban.expiration());

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
            String name = cachedBans.get(index).name();
            new ConfirmGUI(plugin, viewer, "§cРазбанить " + name + "?",
                () -> {
                    me.admin.gui.utils.BanService.pardonProfile(name);
                    plugin.getDatabaseManager().logPunishment("unban", viewer.getName(), name, "Разбан из меню", -1);
                    viewer.sendMessage("§a✓ Игрок " + name + " разбанен.");
                    refresh();
                },
                () -> refresh()
            ).open();
        }
    }

    private List<me.admin.gui.utils.BanService.ProfileBan> getBans() {
        List<me.admin.gui.utils.BanService.ProfileBan> result = new ArrayList<>(me.admin.gui.utils.BanService.profileBans());
        result.sort((a, b) -> {
            if (a.expiration() == null && b.expiration() == null) return 0;
            if (a.expiration() == null) return 1;
            if (b.expiration() == null) return -1;
            return a.expiration().compareTo(b.expiration());
        });
        return result;
    }
}
