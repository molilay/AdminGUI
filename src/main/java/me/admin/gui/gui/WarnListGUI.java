package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.WarnManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class WarnListGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private List<WarnManager.WarnEntry> warns;

    private static final int SLOT_REMOVE_ALL = 46;
    private static final int SLOT_BACK = 48;

    public WarnListGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        return "&8Варны: " + (target.getName() != null ? target.getName() : "?");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        warns = plugin.getWarnManager().getWarns(target.getUniqueId());
        for (int i = 0; i < warns.size(); i++) {
            contentItems.add(buildWarnItem(warns.get(i), i));
        }
    }

    private ItemStack buildWarnItem(WarnManager.WarnEntry entry, int index) {
        return new ItemBuilder(Material.PAPER)
                .name("&eВарн #" + (index + 1))
                .lore(
                        "&7Причина: &f" + entry.reason(),
                        "&7Модератор: &f" + entry.moderator(),
                        "&7Дата: &f" + TimeUtils.formatLogTime(entry.timestamp()),
                        "&7Прошло: &f" + TimeUtils.formatDuration((System.currentTimeMillis() - entry.timestamp()) / 1000) + " назад",
                        "",
                        "&cЛКМ — удалить варн"
                )
                .build();
    }

    @Override
    public void onClick(int slot) {
        SoundUtil.click(viewer);
        if (slot >= 45) {
            if (slot == SLOT_CLOSE) {
                new PlayerCardGUI(plugin, viewer, target).open();
                return;
            }
            if (slot == SLOT_BACK) {
                new PlayerCardGUI(plugin, viewer, target).open();
                return;
            }
            if (slot == SLOT_REMOVE_ALL && !warns.isEmpty()) {
                new ConfirmGUI(plugin, viewer, "&cУдалить все варны " + target.getName() + "?", () -> {
                    for (int i = warns.size() - 1; i >= 0; i--) {
                        plugin.getWarnManager().removeWarn(target.getUniqueId(), i);
                    }
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§a✓ Все варны игрока " + target.getName() + " удалены.");
                    refresh();
                }).open();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < warns.size()) {
            WarnManager.WarnEntry entry = warns.get(index);
            new ConfirmGUI(plugin, viewer, "&cУдалить варн #" + (index + 1) + "?", () -> {
                plugin.getWarnManager().removeWarn(target.getUniqueId(), index);
                SoundUtil.success(viewer);
                viewer.sendMessage("§a✓ Варн #" + (index + 1) + " удалён.");
                refresh();
            }).open();
        }
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = super.buildInventory();
        if (!warns.isEmpty()) {
            inv.setItem(SLOT_REMOVE_ALL, new ItemBuilder(Material.LAVA_BUCKET)
                    .name("&cУдалить все варны")
                    .lore("&7Всего: &e" + warns.size())
                    .build());
        }
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("&7← Назад к карточке игрока")
                .build());
        return inv;
    }
}
