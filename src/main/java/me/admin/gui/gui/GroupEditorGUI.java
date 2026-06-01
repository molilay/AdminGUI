package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.integration.LuckPermsIntegration;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupEditorGUI extends PaginatedGUI {

    private final Group group;
    private String mode = "main"; // main, permissions, parents

    private static final int SLOT_PREFIX = 10;
    private static final int SLOT_SUFFIX = 11;
    private static final int SLOT_WEIGHT = 12;
    private static final int SLOT_PARENTS = 13;
    private static final int SLOT_PERMISSIONS = 14;
    private static final int SLOT_RENAME = 15;
    private static final int SLOT_DELETE = 16;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_ADD_PERM = 46;

    public GroupEditorGUI(AdvancedModeratorGUI plugin, Player viewer, Group group) {
        super(plugin, viewer);
        this.group = group;
    }

    @Override
    public String getTitle() {
        return plugin.getConfigManager().getGuiTitle("title-group-editor", "group",
                group.getDisplayName() != null ? group.getDisplayName() : group.getName());
    }

    @Override
    public void buildContent() {
        contentItems.clear();
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, SIZE, getTitle());
        fillBorder(inv);

        if (mode.equals("main")) {
            buildMainEditor(inv);
        } else if (mode.equals("permissions")) {
            buildPermissionsEditor(inv);
        } else if (mode.equals("parents")) {
            buildParentsEditor(inv);
        }

        inv.setItem(SLOT_MAIN_MENU, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
        return inv;
    }

    private void buildMainEditor(Inventory inv) {
        String displayName = group.getDisplayName() != null ? group.getDisplayName() : group.getName();

        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }

        inv.setItem(SLOT_PREFIX, new ItemBuilder(Material.NAME_TAG)
                .name("&eПрефикс")
                .lore("&7Текущий: &r" + (plugin.getLuckPermsIntegration().getGroupPrefix(group) != null ?
                        plugin.getLuckPermsIntegration().getGroupPrefix(group) : "&7не задан"))
                .build());

        inv.setItem(SLOT_SUFFIX, new ItemBuilder(Material.NAME_TAG)
                .name("&eСуффикс")
                .lore("&7Текущий: &r" + (plugin.getLuckPermsIntegration().getGroupSuffix(group) != null ?
                        plugin.getLuckPermsIntegration().getGroupSuffix(group) : "&7не задан"))
                .build());

        inv.setItem(SLOT_WEIGHT, new ItemBuilder(Material.ANVIL)
                .name("&eВес (приоритет): &f" + group.getWeight().orElse(0))
                .lore("&7Чем выше вес, тем выше приоритет")
                .build());

        inv.setItem(SLOT_PARENTS, new ItemBuilder(Material.BOOKSHELF)
                .name("&eНаследование")
                .lore("&7Родительские группы: &f" + plugin.getLuckPermsIntegration()
                        .getParentGroups(group).size())
                .build());

        inv.setItem(SLOT_PERMISSIONS, new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&eПрава (permissions)")
                .lore("&7Всего прав: &f" + plugin.getLuckPermsIntegration()
                        .getPermissions(group).size())
                .build());

        inv.setItem(SLOT_RENAME, new ItemBuilder(Material.ANVIL)
                .name("&eПереименовать")
                .lore("&7Текущее имя: &f" + displayName)
                .build());

        inv.setItem(SLOT_DELETE, new ItemBuilder(Material.BARRIER)
                .name("&cУдалить группу")
                .lore("&7Будьте осторожны!")
                .build());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("&7← К списку групп")
                .build());
    }

    private void buildPermissionsEditor(Inventory inv) {
        Set<PermissionNode> perms = plugin.getLuckPermsIntegration().getPermissions(group);
        List<PermissionNode> permList = new ArrayList<>(perms);

        int start = page * MAX_ITEMS_PER_PAGE;
        int end = Math.min(start + MAX_ITEMS_PER_PAGE, permList.size());

        int contentSlot = 0;
        for (int i = start; i < end; i++) {
            while (contentSlot == SLOT_BACK) contentSlot++;
            PermissionNode node = permList.get(i);
            inv.setItem(contentSlot++, new ItemBuilder(Material.PAPER)
                    .name((node.getValue() ? "&a" : "&c") + node.getPermission())
                    .lore("&7Значение: " + (node.getValue() ? "&atrue" : "&cfalse"),
                            "&cНажмите чтобы удалить")
                    .build());
        }

        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) permList.size() / MAX_ITEMS_PER_PAGE));
        if (page > 0) inv.setItem(SLOT_PREV, ItemBuilder.createPreviousButton());
        inv.setItem(SLOT_INFO, ItemBuilder.createPageInfo(page, totalPages));
        if (end < permList.size()) inv.setItem(SLOT_NEXT, ItemBuilder.createNextButton());

        inv.setItem(SLOT_ADD_PERM, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a➕ Добавить право")
                .lore("&7Нажмите чтобы добавить новое право")
                .build());
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("&7← Назад к редактору")
                .build());
    }

    private void buildParentsEditor(Inventory inv) {
        List<Group> parents = new ArrayList<>(plugin.getLuckPermsIntegration().getParentGroups(group));
        List<Group> allGroups = plugin.getLuckPermsIntegration().getSortedGroups();

        List<Group> parentItems = new ArrayList<>(parents);
        for (Group g : allGroups) {
            if (!parents.contains(g) && !g.getName().equals(group.getName())) {
                parentItems.add(g);
            }
        }

        int start = page * MAX_ITEMS_PER_PAGE;
        int end = Math.min(start + MAX_ITEMS_PER_PAGE, parentItems.size());

        int contentSlot = 0;
        for (int i = start; i < end; i++) {
            while (contentSlot == SLOT_BACK) contentSlot++;
            Group g = parentItems.get(i);
            boolean isParent = parents.contains(g);
            inv.setItem(contentSlot++, new ItemBuilder(Material.BOOK)
                    .name((isParent ? "&b" : "&7") + g.getName())
                    .lore(isParent ? "&cНажмите чтобы удалить наследование" : "&aНажмите чтобы добавить наследование")
                    .build());
        }

        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) parentItems.size() / MAX_ITEMS_PER_PAGE));
        if (page > 0) inv.setItem(SLOT_PREV, ItemBuilder.createPreviousButton());
        inv.setItem(SLOT_INFO, ItemBuilder.createPageInfo(page, totalPages));
        if (end < parentItems.size()) inv.setItem(SLOT_NEXT, ItemBuilder.createNextButton());

        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("&7← Назад к редактору")
                .build());
    }

    @Override
    public void onClick(int slot) {
        if (slot == SLOT_MAIN_MENU) {
            plugin.getGuiManager().unregister(viewer.getUniqueId());
            new MainMenu(plugin, viewer).open();
            return;
        }
        if (slot == SLOT_CLOSE) {
            close();
            return;
        }

        if (mode.equals("main")) {
            handleMainClick(slot);
        } else if (mode.equals("permissions")) {
            handlePermissionsClick(slot);
        } else if (mode.equals("parents")) {
            handleParentsClick(slot);
        }
    }

    private void handleMainClick(int slot) {
        LuckPermsIntegration lp = plugin.getLuckPermsIntegration();

        switch (slot) {
            case SLOT_PREFIX -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите новый префикс:", input -> {
                    lp.setGroupPrefix(group, input);
                    viewer.sendMessage("§a✓ Префикс изменён.");
                    open();
                });
            }
            case SLOT_SUFFIX -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите новый суффикс:", input -> {
                    lp.setGroupSuffix(group, input);
                    viewer.sendMessage("§a✓ Суффикс изменён.");
                    open();
                });
            }
            case SLOT_WEIGHT -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите новый вес (число):", input -> {
                    try {
                        int weight = Integer.parseInt(input);
                        lp.setGroupWeight(group, weight);
                        viewer.sendMessage("§a✓ Вес изменён.");
                    } catch (NumberFormatException e) {
                        viewer.sendMessage("§cВведите число!");
                    }
                    open();
                });
            }
            case SLOT_PARENTS -> {
                mode = "parents";
                page = 0;
                refresh();
            }
            case SLOT_PERMISSIONS -> {
                mode = "permissions";
                page = 0;
                refresh();
            }
            case SLOT_RENAME -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите новое имя группы:", input -> {
                    lp.renameGroup(group, input);
                    viewer.sendMessage("§a✓ Группа переименована в " + input);
                    new GroupListGUI(plugin, viewer).open();
                });
            }
            case SLOT_DELETE -> {
                new ConfirmGUI(plugin, viewer, "§cУдалить группу " + group.getName() + "?", () -> {
                    lp.deleteGroup(group);
                    viewer.sendMessage(plugin.getConfigManager().getFormattedMessage("group-deleted", "group", group.getName()));
                    new GroupListGUI(plugin, viewer).open();
                }).open();
            }
            case SLOT_BACK -> new GroupListGUI(plugin, viewer).open();
        }
    }

    private void handlePermissionsClick(int slot) {
        if (slot == SLOT_BACK) {
            mode = "main";
            page = 0;
            refresh();
            return;
        }

        if (slot == SLOT_PREV || slot == SLOT_NEXT) {
            handlePaginatedClick(slot);
            return;
        }

        if (slot == SLOT_INFO || slot == SLOT_ADD_PERM) {
            if (slot == SLOT_ADD_PERM) addNewPermission();
            return;
        }

        if (slot == SLOT_MAIN_MENU) return;

        List<PermissionNode> perms = new ArrayList<>(plugin.getLuckPermsIntegration().getPermissions(group));
        int adjustedSlot = slot >= SLOT_BACK ? slot - 1 : slot;
        int index = page * MAX_ITEMS_PER_PAGE + adjustedSlot;
        if (index >= 0 && index < perms.size()) {
            PermissionNode node = perms.get(index);
            plugin.getLuckPermsIntegration().removePermission(group, node.getPermission());
            viewer.sendMessage("§cПраво удалено: §f" + node.getPermission());
            refresh();
            return;
        }

        if (slot >= MAX_ITEMS_PER_PAGE - 1) return;
        addNewPermission();
    }

    private void addNewPermission() {
        viewer.closeInventory();
        plugin.getChatInputManager().awaitInput(viewer, "§eВведите право (например: amgui.use true):", input -> {
            String[] parts = input.split(" ");
            String perm = parts[0];
            boolean value = parts.length > 1 && parts[1].equalsIgnoreCase("false") ? false : true;
            plugin.getLuckPermsIntegration().addPermission(group, perm, value);
            viewer.sendMessage("§a✓ Право добавлено.");
            open();
        });
    }

    private void handleParentsClick(int slot) {
        if (slot == PaginatedGUI.SLOT_BACK) {
            mode = "main";
            page = 0;
            refresh();
            return;
        }
        if (slot == SLOT_PREV || slot == SLOT_NEXT) {
            handlePaginatedClick(slot);
            return;
        }
        if (slot == SLOT_INFO) return;

        LuckPermsIntegration lp = plugin.getLuckPermsIntegration();
        List<Group> parents = new ArrayList<>(lp.getParentGroups(group));
        List<Group> allGroups = lp.getSortedGroups();

        List<Group> combined = new ArrayList<>(parents);
        for (Group g : allGroups) {
            if (!parents.contains(g) && !g.getName().equals(group.getName())) {
                combined.add(g);
            }
        }

        int adjustedSlot = slot >= SLOT_BACK ? slot - 1 : slot;
        int index = page * MAX_ITEMS_PER_PAGE + adjustedSlot;
        if (index >= 0 && index < combined.size()) {
            Group clicked = combined.get(index);
            if (parents.contains(clicked)) {
                lp.removeParent(group, clicked);
            } else {
                lp.addParent(group, clicked);
            }
            refresh();
        }
    }

    @Override
    public void open() {
        viewer.openInventory(buildInventory());
        plugin.getGuiManager().register(viewer.getUniqueId(), this);
    }
}
