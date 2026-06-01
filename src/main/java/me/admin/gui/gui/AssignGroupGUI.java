package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import net.luckperms.api.model.group.Group;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AssignGroupGUI extends PaginatedGUI {

    private final OfflinePlayer target;

    public AssignGroupGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-assign-group");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        List<Group> groups = plugin.getLuckPermsIntegration().getSortedGroups();

        for (Group group : groups) {
            ItemBuilder builder = new ItemBuilder(Material.WHITE_WOOL)
                    .name("&b" + (group.getDisplayName() != null ? group.getDisplayName() : group.getName()))
                    .lore("&7Вес: &f" + group.getWeight().orElse(0));

            String prefix = plugin.getLuckPermsIntegration().getGroupPrefix(group);
            if (prefix != null) builder.lore("&7Префикс: &r" + prefix);

            builder.lore("",
                    "&eЛКМ: Назначить навсегда",
                    "&eПКМ: Назначить временно");

            contentItems.add(builder.build());
        }
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) {
                close();
                viewer.closeInventory();
                new PlayerCardGUI(plugin, viewer, target).open();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        List<Group> groups = plugin.getLuckPermsIntegration().getSortedGroups();

        if (index >= 0 && index < groups.size()) {
            Group group = groups.get(index);
            promptDuration(group);
        }
    }

    private void promptDuration(Group group) {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите длительность (1д, 7д, perm):", input -> {
            long seconds = 0;
            if (!input.equalsIgnoreCase("perm") && !input.equalsIgnoreCase("навсегда")) {
                seconds = TimeUtils.parseDuration(input);
            }
            Long duration = seconds > 0 ? seconds : null;

            plugin.getLuckPermsIntegration().setGroup(
                    target.getUniqueId(), target.getName(), group, duration);

            String groupName = group.getDisplayName() != null ? group.getDisplayName() : group.getName();
            if (duration != null) {
                viewer.sendMessage("§a✓ Игроку " + target.getName() +
                        " назначена группа " + groupName +
                        " на " + TimeUtils.formatDuration(duration));
            } else {
                viewer.sendMessage("§a✓ Игроку " + target.getName() +
                        " назначена группа " + groupName + " навсегда");
            }
        });
    }
}
