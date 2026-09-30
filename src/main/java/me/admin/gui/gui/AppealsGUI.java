package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.AppealManager;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class AppealsGUI extends PaginatedGUI {

    private List<AppealManager.AppealEntry> pendingAppeals;

    public AppealsGUI(AdvancedModeratorGUI plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    public String getTitle() {
        return "§8Апелляции";
    }

    @Override
    public void buildContent() {
        contentItems.clear();
        pendingAppeals = plugin.getAppealManager().getPending();

        for (AppealManager.AppealEntry e : pendingAppeals) {
            contentItems.add(new ItemBuilder(Material.BOOK)
                    .name("&6#" + e.id() + " &f" + e.targetName())
                    .lore(
                            "&7От: &f" + e.submitter(),
                            "&7Причина: &f" + e.reason(),
                            "&7Время: &f" + e.getFormattedDate(),
                            "",
                            "&aЛКМ — принять (разбанить)",
                            "&cПКМ — отклонить",
                            "&7Принять: введите причину в чат"
                    )
                    .build());
        }
    }

    @Override
    public void onClick(int slot) {
        onClick(slot, false, false);
    }

    @Override
    public void onClick(int slot, boolean shift) {
        onClick(slot, shift, false);
    }

    @Override
    public void onClick(int slot, boolean shift, boolean right) {
        if (slot >= 45) {
            if (slot == PaginatedGUI.SLOT_CLOSE) { close(); return; }
            if (slot == PaginatedGUI.SLOT_MAIN_MENU) {
                openHome();
                return;
            }
            handlePaginatedClick(slot);
            return;
        }

        int index = page * MAX_ITEMS_PER_PAGE + slot;
        if (index < 0 || index >= pendingAppeals.size()) return;

        AppealManager.AppealEntry entry = pendingAppeals.get(index);
        SoundUtil.click(viewer);

        if (right) {
            promptDeny(entry);
            return;
        }

        promptAccept(entry);
    }

    private void promptAccept(AppealManager.AppealEntry entry) {
        new ConfirmGUI(plugin, viewer, "§aПринять апелляцию #" + entry.id() + "?",
            () -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eПричина принятия апелляции (или 'нет'):", input -> {
                    String response = input.trim();
                    plugin.getAppealManager().accept(entry.id(), response, viewer.getName());
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§a✓ Апелляция #" + entry.id() + " принята. §f" + entry.targetName() + " §aразбанен.");
                    new AppealsGUI(plugin, viewer).open();
                });
            },
            () -> new AppealsGUI(plugin, viewer).open()
        ).open();
    }

    private void promptDeny(AppealManager.AppealEntry entry) {
        new ConfirmGUI(plugin, viewer, "§cОтклонить апелляцию #" + entry.id() + "?",
            () -> {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eПричина отклонения (или 'нет'):", input -> {
                    String response = input.trim();
                    plugin.getAppealManager().deny(entry.id(), response, viewer.getName());
                    SoundUtil.success(viewer);
                    viewer.sendMessage("§c✓ Апелляция #" + entry.id() + " отклонена.");
                    new AppealsGUI(plugin, viewer).open();
                });
            },
            () -> new AppealsGUI(plugin, viewer).open()
        ).open();
    }

    @Override
    public void open() {
        super.open();
    }
}
