package me.admin.gui.gui;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.function.Consumer;

public class BanDurationGUI {

    private final AdvancedModeratorGUI plugin;
    private final Player viewer;
    private final Consumer<Long> callback;
    private final String type; // "ban" or "mute"

    public BanDurationGUI(AdvancedModeratorGUI plugin, Player viewer, String type, Consumer<Long> callback) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        this.callback = callback;
    }

    public void open() {
        List<String> allDurations = type.equals("mute")
                ? plugin.getConfigManager().getMuteDurations()
                : plugin.getConfigManager().getTempbanDurations();
        int maxSlots = 54;
        int needed = allDurations.size() + 2;
        final List<String> durations = needed > maxSlots
                ? allDurations.subList(0, maxSlots - 2)
                : allDurations;

        int size = (int) (Math.ceil((durations.size() + 2) / 9.0) * 9);
        size = Math.max(27, Math.min(size, 54));

        Inventory inv = Bukkit.createInventory(null, size,
                plugin.getConfigManager().getGuiTitle("title-ban-duration"));

        int slot = 0;
        for (String dur : durations) {
            long seconds = TimeUtils.parseDuration(dur);
            inv.setItem(slot++, new ItemBuilder(Material.CLOCK)
                    .name("&e" + dur)
                    .lore("&7" + TimeUtils.formatDuration(seconds))
                    .build());
        }

        if (slot < size) {
            inv.setItem(slot++, new ItemBuilder(Material.REDSTONE_BLOCK)
                    .name("&cНавсегда")
                    .lore("&7Перманентный бан")
                    .build());
        }

        if (slot < size) {
            inv.setItem(slot, new ItemBuilder(Material.ANVIL)
                    .name("&bСвоя длительность")
                    .lore("&7Введите длительность (1ч, 7д, 30д...)")
                    .build());
        }

        viewer.openInventory(inv);
        plugin.getGuiManager().register(viewer.getUniqueId(), new PaginatedGUI(plugin, viewer) {
            @Override public String getTitle() { return plugin.getConfigManager().getGuiTitle("title-ban-duration"); }
            @Override public void buildContent() {}
            @Override
            public void onClick(int s) {
                if (s >= 0 && s < durations.size()) {
                    viewer.closeInventory();
                    callback.accept(TimeUtils.parseDuration(durations.get(s)));
                } else if (s == durations.size()) {
                    viewer.closeInventory();
                    callback.accept(-1L);
                } else if (s == durations.size() + 1) {
                    viewer.closeInventory();
                    openCustomDuration();
                }
            }

            private void openCustomDuration() {
                viewer.closeInventory();
                plugin.getChatInputManager().awaitInput(viewer, "§eВведите длительность (например: 1ч, 7д, 30д):", input -> {
                    long seconds = TimeUtils.parseDuration(input);
                    if (seconds <= 0) {
                        viewer.sendMessage("§cНекорректная длительность.");
                        open();
                        return;
                    }
                    callback.accept(seconds);
                });
            }
        });
    }
}
