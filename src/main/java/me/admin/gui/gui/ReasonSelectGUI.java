package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.function.Consumer;

public class ReasonSelectGUI {

    private final AdvancedModeratorGUI plugin;
    private final Player viewer;
    private final String type;
    private final Consumer<String> callback;

    public ReasonSelectGUI(AdvancedModeratorGUI plugin, Player viewer, String type, Consumer<String> callback) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        this.callback = callback;
    }

    public void open() {
        List<String> reasons = switch (type) {
            case "ban" -> plugin.getConfigManager().getBanReasons();
            case "warn" -> plugin.getConfigManager().getWarnReasons();
            default -> plugin.getConfigManager().getKickReasons();
        };

        int calculatedSize = (int) (Math.ceil((reasons.size() + 1) / 9.0) * 9);
        int size = Math.max(27, Math.min(calculatedSize, 54));

        Inventory inv = Bukkit.createInventory(null, size,
                plugin.getConfigManager().getGuiTitle("title-reason"));

        int slot = 0;
        for (String reason : reasons) {
            inv.setItem(slot++, new ItemBuilder(Material.PAPER)
                    .name("&e" + reason)
                    .lore("&7Нажмите, чтобы выбрать")
                    .build());
        }

        inv.setItem(size - 1, new ItemBuilder(Material.ANVIL)
                .name("&bСвоя причина")
                .lore("&7Введите свою причину")
                .build());

        viewer.openInventory(inv);
        plugin.getGuiManager().register(viewer.getUniqueId(), new PaginatedGUI(plugin, viewer) {
            @Override public String getTitle() { return plugin.getConfigManager().getGuiTitle("title-reason"); }
            @Override public void buildContent() {}
            @Override
            public void onClick(int s) {
                if (s >= 0 && s < reasons.size()) {
                    viewer.closeInventory();
                    callback.accept(reasons.get(s));
                } else if (s == size - 1) {
                    openCustomReasonInput();
                }
            }

            private void openCustomReasonInput() {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, plugin.getConfigManager().getMessage("custom-reason-prompt"), input -> {
                    callback.accept(input);
                });
            }
        });
    }
}
