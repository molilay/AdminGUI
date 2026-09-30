package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StaffKitsManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final Map<String, List<KitItem>> kits = new LinkedHashMap<>();

    public record KitItem(Material material, int amount, String name, List<String> lore) {}

    public StaffKitsManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "staffkits.yml");
        load();
    }

    public List<String> getKitNames() { return List.copyOf(kits.keySet()); }

    public void giveKit(Player staff, String kitName) {
        List<KitItem> items = kits.get(kitName.toLowerCase());
        if (items == null) { staff.sendMessage("§cКит '" + kitName + "' не найден."); return; }
        for (KitItem item : items) {
            ItemStack stack = new ItemBuilder(item.material())
                    .amount(item.amount())
                    .name(item.name())
                    .lore(item.lore())
                    .build();
            staff.getInventory().addItem(stack);
        }
        staff.sendMessage("§a✓ Кит §f" + kitName + " §aвыдан.");
    }

    public void addKit(String name, List<KitItem> items) {
        kits.put(name.toLowerCase(), items);
        save();
    }

    public void removeKit(String name) {
        kits.remove(name.toLowerCase());
        save();
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, List<KitItem>> entry : kits.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (KitItem item : entry.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("material", item.material().name());
                m.put("amount", item.amount());
                m.put("name", item.name());
                m.put("lore", item.lore());
                list.add(m);
            }
            config.set("kits." + entry.getKey(), list);
        }
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "staff kits");
    }

    private void load() {
        if (!dataFile.exists()) {
            kits.put("moderator", List.of(
                    new KitItem(Material.COMPASS, 1, "&aКомпас ТП", List.of("&7Клик по игроку для ТП")),
                    new KitItem(Material.BOOK, 1, "&eСписок игроков", List.of("&7Клик — открыть список")),
                    new KitItem(Material.BLAZE_ROD, 1, "&cУдар-кик", List.of("&7Удар мобов = кик")),
                    new KitItem(Material.PACKED_ICE, 1, "&bЗаморозка", List.of("&7Клик = заморозить игрока")),
                    new KitItem(Material.ENDER_PEARL, 16, "&dЖемчуг", List.of("&7Для перемещения"))
            ));
            save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        org.bukkit.configuration.ConfigurationSection kitsSec = yml.getConfigurationSection("kits");
        if (kitsSec == null) return;
        for (String key : kitsSec.getKeys(false)) {
            List<Map<?, ?>> raw = yml.getMapList("kits." + key);
            List<KitItem> items = new ArrayList<>();
            for (Map<?, ?> m : raw) {
                try {
                    items.add(new KitItem(
                            Material.valueOf((String) m.get("material")),
                            ((Number) m.get("amount")).intValue(),
                            (String) m.get("name"),
                            m.get("lore") instanceof List<?> lore
                                    ? lore.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                                    : List.of()
                    ));
                } catch (Exception ignored) {}
            }
            kits.put(key, items);
        }
    }

    public static class StaffKitsGUI extends me.admin.gui.gui.PaginatedGUI {
        public StaffKitsGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }

        @Override
        public String getTitle() { return "§8Наборы персонала"; }

        @Override
        public void buildContent() {
            contentItems.clear();
            for (String name : plugin.getStaffKitsManager().getKitNames()) {
                contentItems.add(new ItemBuilder(Material.CHEST)
                        .name("&6" + name)
                        .lore("&aЛКМ — получить набор")
                        .build());
            }
        }

        @Override
        public void onClick(int slot) {
            if (slot >= 45) {
                if (slot == SLOT_MAIN_MENU) {
                    plugin.getGuiManager().unregister(viewer.getUniqueId());
                    new me.admin.gui.gui.MainMenu(plugin, viewer).open();
                    return;
                }
                handlePaginatedClick(slot);
                return;
            }
            int index = page * MAX_ITEMS_PER_PAGE + slot;
            List<String> names = plugin.getStaffKitsManager().getKitNames();
            if (index < 0 || index >= names.size()) return;
            plugin.getStaffKitsManager().giveKit(viewer, names.get(index));
        }
    }
}
