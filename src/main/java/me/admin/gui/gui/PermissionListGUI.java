package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.integration.LuckPermsIntegration;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Non-blocking LuckPerms permission viewer/editor with fail-closed callbacks. */
public class PermissionListGUI extends PaginatedGUI {

    private final OfflinePlayer target;
    private List<PermissionNode> permissions = new ArrayList<>();
    private boolean loading = true;
    private boolean mutationRunning;
    private String loadError = "";

    public PermissionListGUI(AdvancedModeratorGUI plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    public String getTitle() {
        return "&8Права: " + (target.getName() != null ? target.getName() : "?");
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        if (loading) {
            contentItems.add(new ItemBuilder(Material.CLOCK).name("&eЗагрузка прав...")
                    .lore("&7LuckPerms загружается асинхронно", "&7без остановки серверного тика.").build());
            return;
        }
        if (!loadError.isBlank()) {
            contentItems.add(new ItemBuilder(Material.BARRIER).name("&cПрава не загружены")
                    .lore("&7Безопасный отказ: " + loadError, "", "&eЗакройте меню и попробуйте снова.").build());
            return;
        }
        for (PermissionNode node : permissions) {
            String permission = node.getPermission();
            boolean value = node.getValue();
            ItemStack item = new ItemBuilder(value ? Material.LIME_WOOL : Material.RED_WOOL)
                    .name((value ? "&a" : "&c") + permission)
                    .lore(value ? "&7✓ Разрешено" : "&c✗ Запрещено", "",
                            viewer.hasPermission("amgui.permissions.edit")
                                    ? "&7Кликните, чтобы " + (value ? "запретить" : "разрешить")
                                    : "&8Только просмотр")
                    .build();
            contentItems.add(item);
        }
        if (contentItems.isEmpty()) {
            contentItems.add(new ItemBuilder(Material.BARRIER).name("&7Нет прав")
                    .lore("&7У игрока нет отдельных прав", "&7(используются групповые)").build());
        }
    }

    @Override
    public void open() {
        if (!viewer.hasPermission("amgui.permissions")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        loading = true;
        loadError = "";
        permissions = new ArrayList<>();
        super.open();
        loadPermissions();
    }

    private void loadPermissions() {
        loading = true;
        loadError = "";
        LuckPermsIntegration lp = plugin.getLuckPermsIntegration();
        long timeout = lookupTimeout();
        lp.findUserPermissionAsync(viewer.getUniqueId(), "amgui.permissions")
                .orTimeout(timeout, TimeUnit.MILLISECONDS)
                .thenCompose(permission -> permission.orElse(false)
                        ? lp.getPermissionsAsync(target.getUniqueId()).orTimeout(timeout, TimeUnit.MILLISECONDS)
                        : CompletableFuture.failedFuture(new SecurityException("право отозвано")))
                .whenComplete((nodes, failure) -> plugin.getTaskExecutor().runOnMain(() -> {
                    if (plugin.getGuiManager().getOpenGUI(viewer) != this) return;
                    loading = false;
                    if (failure != null || nodes == null) {
                        loadError = safeFailure(failure);
                        permissions = new ArrayList<>();
                    } else {
                        loadError = "";
                        permissions = new ArrayList<>(nodes);
                    }
                    refresh();
                }));
    }

    @Override
    public void onClick(int slot) {
        if (slot >= 45) {
            handlePaginatedClick(slot);
            return;
        }
        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (loading || !loadError.isBlank() || mutationRunning || index < 0 || index >= permissions.size()) return;
        if (!viewer.hasPermission("amgui.permissions.edit")) {
            viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        PermissionNode node = permissions.get(index);
        LuckPermsIntegration lp = plugin.getLuckPermsIntegration();
        String permission = node.getPermission();
        boolean newValue = !node.getValue();
        java.util.UUID actorId = viewer.getUniqueId();
        String actorName = viewer.getName();
        java.util.UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        long timeout = lookupTimeout();
        mutationRunning = true;
        lp.findUserPermissionAsync(viewer.getUniqueId(), "amgui.permissions.edit")
                .orTimeout(timeout, TimeUnit.MILLISECONDS)
                .thenCompose(allowed -> allowed.orElse(false)
                        ? lp.setUserPermissionAsync(target.getUniqueId(), permission, newValue)
                                .orTimeout(timeout, TimeUnit.MILLISECONDS)
                        : CompletableFuture.failedFuture(new SecurityException("право отозвано")))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) plugin.getAuditManager().tryRecord(actorId, actorName, "permission.change",
                            targetName, targetId, "permission=" + permission + "; value=" + newValue);
                    plugin.getTaskExecutor().runOnMain(() -> {
                    mutationRunning = false;
                    if (plugin.getGuiManager().getOpenGUI(viewer) != this) return;
                    if (failure != null) {
                        viewer.sendMessage("§cИзменение права отклонено: " + safeFailure(failure));
                        loadPermissions();
                        return;
                    }
                    permissions.set(index, PermissionNode.builder(permission).value(newValue).build());
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§a✓ Право §f" + permission + " §a→ "
                            + (newValue ? "§aразрешено" : "§cзапрещено"));
                    refresh();
                    });
                });
    }

    private long lookupTimeout() {
        return Math.clamp(plugin.getConfig().getLong("runtime.luckperms-timeout-ms", 3000L), 250L, 10_000L);
    }

    private static String safeFailure(Throwable failure) {
        if (failure == null) return "операция отменена";
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @Override
    protected Inventory buildInventory() {
        Inventory inventory = Bukkit.createInventory(null, SIZE, me.admin.gui.utils.TextUtil.legacy(getTitle()));
        int start = page * MAX_ITEMS_PER_PAGE;
        int end = Math.min(start + MAX_ITEMS_PER_PAGE, contentItems.size());
        for (int i = start; i < end; i++) inventory.setItem(i - start, contentItems.get(i));
        for (int i = 45; i < SIZE; i++) if (inventory.getItem(i) == null) inventory.setItem(i, ItemBuilder.createFiller());
        int totalPages = Math.max(1, (int) Math.ceil((double) contentItems.size() / MAX_ITEMS_PER_PAGE));
        if (page > 0) inventory.setItem(45, ItemBuilder.createPreviousButton());
        inventory.setItem(49, ItemBuilder.createPageInfo(page, totalPages));
        if (end < contentItems.size()) inventory.setItem(53, ItemBuilder.createNextButton());
        inventory.setItem(52, ItemBuilder.createCloseButton());
        return inventory;
    }
}
