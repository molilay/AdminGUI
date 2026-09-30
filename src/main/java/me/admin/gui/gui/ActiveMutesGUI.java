package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.MuteManager;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActiveMutesGUI extends PaginatedGUI {

    private List<Map.Entry<UUID, MuteManager.MuteEntry>> cachedMutes;

    public ActiveMutesGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-active-mutes");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        cachedMutes = new ArrayList<>(plugin.getMuteManager().getMutedEntries().entrySet());
        for (Map.Entry<UUID, MuteManager.MuteEntry> e : cachedMutes) {
            MuteManager.MuteEntry mute = e.getValue();
            String name = mute.targetName() != null && !mute.targetName().isEmpty() ? mute.targetName() : "Unknown";
            String reason = mute.reason();
            String moderator = mute.moderator();

            ItemBuilder builder = new ItemBuilder(Material.PAPER)
                    .name("&c" + name)
                    .lore(
                            "&7Причина: &f" + reason,
                            "&7Модератор: &f" + moderator,
                            "&7Осталось: " + plugin.getMuteManager().formatRemaining(mute)
                    );

            builder.lore("", "&eЛКМ — размьютить");
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
        if (cachedMutes == null) cachedMutes = new ArrayList<>(plugin.getMuteManager().getMutedEntries().entrySet());

        if (index >= 0 && index < cachedMutes.size()) {
            UUID uuid = cachedMutes.get(index).getKey();
            MuteManager.MuteEntry mute = cachedMutes.get(index).getValue();
            String name = mute.targetName() != null && !mute.targetName().isEmpty() ? mute.targetName() : "Unknown";

            new ConfirmGUI(plugin, viewer, "§cРазмьютить " + name + "?", () -> {
                plugin.getMuteManager().unmute(uuid);
                plugin.getDatabaseManager().logPunishment("unmute", viewer.getName(), name, "Размьют из меню", -1);
                viewer.sendMessage("§a✓ Игрок " + name + " размьючен.");
                refresh();
            }).open();
        }
    }
}
