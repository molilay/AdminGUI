package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AltDetector;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

/** Explainable graph-like list of accounts connected through historical IPs. */
public final class AltConnectionsGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private List<AltDetector.RelatedAccount> related = List.of();

    public AltConnectionsGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override public String getTitle() { return plugin.getLocalizationManager().format("gui.alt-connections.title",
            "&8Связи аккаунтов: %player%", "player", target.getName() == null ? "?" : target.getName()); }

    @Override
    public void buildContent() {
        contentItems.clear();
        related = plugin.getAltDetector().findRelatedAccounts(target.getUniqueId());
        for (AltDetector.RelatedAccount account : related) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(account.uuid());
            ItemBuilder item = player.getName() == null ? new ItemBuilder(Material.PLAYER_HEAD)
                    : new ItemBuilder(plugin.getHeadCacheManager().getHead(player));
            List<String> ipLines = viewer.hasPermission("amgui.viewip")
                    ? account.sharedIps().stream().limit(3).map(ip -> "&8• &f" + ip).toList()
                    : List.of("&8IP скрыты: нет права amgui.viewip");
            item.name((player.isOnline() ? "&a" : "&7") + account.name())
                    .lore("&7Общих IP: &f" + account.sharedIps().size(),
                            "&7Последняя активность: &f" + (account.lastSeen() > 0 ? TimeUtils.formatLogTime(account.lastSeen()) : "—"));
            item.lore(ipLines);
            item.lore("", "&eКлик — открыть карточку игрока");
            contentItems.add(item.build());
        }
        if (related.isEmpty()) contentItems.add(new ItemBuilder(Material.LIME_DYE).name("&aСвязанные аккаунты не найдены")
                .lore("&7Проверена полная история IP,", "&7а не только последний адрес.").build());
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) { handlePaginatedClick(slot); return; }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= related.size()) return;
        AltDetector.RelatedAccount account = related.get(index);
        plugin.getAuditManager().record(viewer, "alts.relationship-view", account.name(), account.uuid(),
                "source=" + target.getUniqueId() + "; shared=" + account.sharedIps().size());
        new PlayerCardGUI(plugin, viewer, Bukkit.getOfflinePlayer(account.uuid())).open();
    }
}
