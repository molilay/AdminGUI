package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ActiveIpBansGUI extends PaginatedGUI {

    private List<me.admin.gui.utils.BanService.IpBan> cachedBans;

    public ActiveIpBansGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-active-ipbans");
    }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.viewip") || !viewer.hasPermission("amgui.ban")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        super.open();
        plugin.getAuditManager().record(viewer, "ipban.list-view", "active-ip-bans", null, "raw-ip=true");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        cachedBans = getIpBans();
        for (me.admin.gui.utils.BanService.IpBan ban : cachedBans) {
            String ip = ban.address();
            String reason = ban.reason();
            String source = ban.source();

            ItemBuilder builder = new ItemBuilder(Material.IRON_TRAPDOOR)
                    .name("&c" + ip)
                    .lore(
                            "&7Причина: &f" + reason,
                            "&7Источник: &f" + source
                    );

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
        if (cachedBans == null) cachedBans = getIpBans();

        if (index >= 0 && index < cachedBans.size()) {
            String ip = cachedBans.get(index).address();

            new ConfirmGUI(plugin, viewer, "§cРазбанить IP " + ip + "?", () -> {
                me.admin.gui.utils.BanService.pardonIp(ip);
                plugin.getDatabaseManager().logPunishment("unban", viewer.getName(), ip, "Разбан IP из меню", -1);
                viewer.sendMessage("§a✓ IP " + ip + " разбанен.");
                refresh();
            }).open();
        }
    }

    private List<me.admin.gui.utils.BanService.IpBan> getIpBans() {
        return new ArrayList<>(me.admin.gui.utils.BanService.ipBans());
    }
}
