package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import net.luckperms.api.model.group.Group;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class GroupListGUI extends PaginatedGUI {

    public GroupListGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-groups");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        List<Group> groups = plugin.getLuckPermsIntegration().getSortedGroups();

        for (Group group : groups) {
            contentItems.add(buildGroupItem(group));
        }

        contentItems.add(new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a+ Создать группу")
                .lore("&7Создать новую группу LuckPerms")
                .glowing()
                .build());
    }

    private ItemStack buildGroupItem(Group group) {
        String prefix = plugin.getLuckPermsIntegration().getGroupPrefix(group);
        String suffix = plugin.getLuckPermsIntegration().getGroupSuffix(group);
        int weight = plugin.getLuckPermsIntegration().getGroupWeight(group);
        long players = plugin.getLuckPermsIntegration().getPlayerCountInGroup(group);
        int permsCount = plugin.getLuckPermsIntegration().getPermissions(group).size();

        String displayName = group.getDisplayName() != null ? group.getDisplayName() : group.getName();

        ItemBuilder builder = new ItemBuilder(Material.WHITE_WOOL)
                .name("&b" + displayName)
                .lore(
                        "&7Имя: &f" + group.getName(),
                        "&7Вес: &f" + weight,
                        "&7Игроков: &f" + players,
                        "&7Прав: &f" + permsCount
                );

        if (prefix != null) builder.lore("&7Префикс: &r" + prefix);
        if (suffix != null) builder.lore("&7Суффикс: &r" + suffix);

        builder.lore("", "&eКликните для редактирования");
        return builder.build();
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) {
                close();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        List<Group> groups = plugin.getLuckPermsIntegration().getSortedGroups();

        if (index >= 0 && index < groups.size()) {
            if (!viewer.hasPermission("amgui.groups.edit")) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            new GroupEditorGUI(plugin, viewer, groups.get(index)).open();
        } else if (index == groups.size()) {
            if (!viewer.hasPermission("amgui.groups.edit")) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            createNewGroup();
        }
    }

    private void createNewGroup() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, plugin.getConfigManager().getMessage("custom-reason-prompt"), input -> {
            if (input.isEmpty()) {
                viewer.sendMessage("§cНазвание группы не может быть пустым.");
                open();
                return;
            }
            plugin.getLuckPermsIntegration().createGroup(input).thenAccept(group -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    viewer.sendMessage(plugin.getConfigManager().getFormattedMessage("group-created", "group", input));
                    new GroupEditorGUI(plugin, viewer, group).open();
                });
            });
        });
    }
}
