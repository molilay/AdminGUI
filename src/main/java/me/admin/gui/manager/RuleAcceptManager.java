package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RuleAcceptManager {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> accepted = ConcurrentHashMap.newKeySet();
    private final File dataFile;

    public RuleAcceptManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "rule-accept.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("rule-accept.enabled", false);
    }

    public boolean hasAccepted(Player player) {
        return accepted.contains(player.getUniqueId()) || player.hasPermission("amgui.admin");
    }

    public void accept(Player player) {
        accepted.add(player.getUniqueId());
        save();
    }

    public List<String> getRules() {
        return plugin.getConfig().getStringList("rule-accept.rules");
    }

    public void openAcceptGUI(Player player) {
        List<String> rules = getRules();
        Inventory inv = Bukkit.createInventory(null, 27, me.admin.gui.utils.TextUtil.legacy("§8Правила сервера"));

        for (int i = 0; i < Math.min(rules.size(), 18); i++) {
            inv.setItem(i, new ItemBuilder(Material.PAPER)
                    .name("&eПравило #" + (i + 1))
                    .lore(rules.get(i).replace("&", "§"))
                    .build());
        }

        inv.setItem(11, new ItemBuilder(Material.GREEN_WOOL)
                .name("&a✓ Принимаю правила")
                .lore("&7Нажмите чтобы принять")
                .glowing()
                .build());

        inv.setItem(15, new ItemBuilder(Material.RED_WOOL)
                .name("&cОтказываюсь")
                .lore("&7Будете кикнуты")
                .build());

        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, ItemBuilder.createFiller());
        }

        me.admin.gui.gui.PaginatedGUI menu = new me.admin.gui.gui.PaginatedGUI(plugin, player) {
            @Override public String getTitle() { return "§8Правила сервера"; }
            @Override public void buildContent() {}
            @Override protected Inventory buildInventory() { return inv; }
            @Override public void onClick(int slot) {
                if (slot == 11) {
                    accept(player);
                    player.closeInventory();
                    player.sendMessage("§a✓ Вы приняли правила сервера.");
                    plugin.getGuiManager().unregister(player.getUniqueId());
                } else if (slot == 15) {
                    player.kick(me.admin.gui.utils.TextUtil.legacy("§cВы не приняли правила сервера.\n§7Примите правила при следующем входе."));
                }
            }
        };
        plugin.getGuiManager().open(player, menu, inv, true);
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> list = accepted.stream().map(UUID::toString).toList();
        config.set("accepted", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "rule acceptance");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : config.getStringList("accepted")) {
            try { accepted.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }
}
