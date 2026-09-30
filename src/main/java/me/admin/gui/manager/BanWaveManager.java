package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PaginatedGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.SoundUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BanWaveManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final List<BanEntry> queue = new ArrayList<>();
    private boolean executing = false;

    public record BanEntry(String playerName, UUID playerUuid, String reason, String addedBy, long addedAt) {
        public String getFormattedDate() { return TimeUtils.formatLogTime(addedAt); }
    }

    public BanWaveManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "banwave.yml");
        load();
    }

    public void add(String playerName, UUID playerUuid, String reason, String addedBy) {
        queue.removeIf(e -> e.playerName().equalsIgnoreCase(playerName));
        queue.add(new BanEntry(playerName, playerUuid, reason, addedBy, System.currentTimeMillis()));
        save();
    }

    public void remove(String playerName) {
        queue.removeIf(e -> e.playerName().equalsIgnoreCase(playerName));
        save();
    }

    public void remove(int index) {
        if (index >= 0 && index < queue.size()) { queue.remove(index); save(); }
    }

    public List<BanEntry> getQueue() { return Collections.unmodifiableList(queue); }
    public int getQueueSize() { return queue.size(); }

    public void executeBanWave(Player executor) {
        if (executing) { executor.sendMessage("§cБан-волна уже выполняется."); return; }
        if (queue.isEmpty()) { executor.sendMessage("§cОчередь пуста."); return; }
        int batchSize = Math.min(plugin.getConfig().getInt("banwave.per-wave", 5), queue.size());
        if (batchSize > plugin.getPunishmentSecurityManager().getMassActionLimit(executor)) {
            executor.sendMessage("§cПревышен разрешённый размер массового действия.");
            return;
        }
        for (int i = 0; i < batchSize; i++) {
            BanEntry entry = queue.get(i);
            var target = Bukkit.getOfflinePlayer(entry.playerUuid());
            var decision = plugin.getPunishmentSecurityManager().validate(executor, target, "ban");
            if (!decision.allowed()) {
                executor.sendMessage("§cБан-волна остановлена для " + entry.playerName() + ": " + decision.reason());
                return;
            }
        }
        executing = true;

        int delay = plugin.getConfig().getInt("banwave.delay-between", 20);
        int perWave = Math.max(1, plugin.getConfig().getInt("banwave.per-wave", 5));
        int[] count = {0};

        for (int i = 0; i < Math.min(perWave, queue.size()); i++) {
            BanEntry entry = queue.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String reason = "[BanWave] " + entry.reason();
                me.admin.gui.utils.BanService.banProfile(entry.playerName(), reason, null, executor.getName());
                Player online = Bukkit.getPlayer(entry.playerUuid());
                if (online != null) online.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены бан-волной.\n§7Причина: " + entry.reason()));
                plugin.getDatabaseManager().logPunishment("ban", executor.getName(), entry.playerName(), reason, -1);
                count[0]++;
                if (count[0] >= perWave || count[0] >= queue.size()) {
                    queue.clear();
                    save();
                    executing = false;
                    executor.sendMessage("§c✓ Бан-волна завершена. Всего: " + count[0]);
                }
            }, i * (long) delay);
        }
    }

    public void clear() { queue.clear(); save(); }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (BanEntry e : queue) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player", e.playerName());
            m.put("uuid", e.playerUuid().toString());
            m.put("reason", e.reason());
            m.put("by", e.addedBy());
            m.put("added", e.addedAt());
            list.add(m);
        }
        config.set("queue", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "ban wave");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<?, ?>> raw = config.getMapList("queue");
        for (Map<?, ?> m : raw) {
            try {
                queue.add(new BanEntry(
                        (String) m.get("player"),
                        UUID.fromString((String) m.get("uuid")),
                        (String) m.get("reason"),
                        (String) m.get("by"),
                        ((Number) m.get("added")).longValue()
                ));
            } catch (Exception ignored) {}
        }
    }

    public static class BanWaveGUI extends PaginatedGUI {
        public BanWaveGUI(AdvancedModeratorGUI plugin, Player viewer) { super(plugin, viewer); }

        @Override
        public String getTitle() { return "§8Бан-волна"; }

        @Override
        public void buildContent() {
            contentItems.clear();
            for (BanWaveManager.BanEntry e : plugin.getBanWaveManager().getQueue()) {
                contentItems.add(new ItemBuilder(Material.REDSTONE_BLOCK)
                        .name("&c" + e.playerName())
                        .lore(
                                "&7Причина: &f" + e.reason(),
                                "&7Добавил: &f" + e.addedBy(),
                                "&7Время: &f" + e.getFormattedDate(),
                                "",
                                "&cПКМ — убрать из очереди"
                        ).build());
            }
        }

        @Override
        public void onClick(int slot) {
            onClick(slot, false);
        }

        @Override
        public void onClick(int slot, boolean shift) {
            if (slot >= 45) {
                if (slot == SLOT_MAIN_MENU) {
                    plugin.getGuiManager().unregister(viewer.getUniqueId());
                    new me.admin.gui.gui.MainMenu(plugin, viewer).open();
                    return;
                }
                if (slot == 46 && !plugin.getBanWaveManager().getQueue().isEmpty()) {
                    new me.admin.gui.gui.ConfirmGUI(plugin, viewer, "§cЗапустить бан-волну? (" + plugin.getBanWaveManager().getQueueSize() + " игроков)",
                        () -> {
                            plugin.getBanWaveManager().executeBanWave(viewer);
                            viewer.sendMessage("§6⏳ Бан-волна запущена...");
                            close();
                        },
                        () -> {
                            new BanWaveGUI(plugin, viewer).open();
                        }
                    ).open();
                    return;
                }
                handlePaginatedClick(slot);
                return;
            }
            int index = page * MAX_ITEMS_PER_PAGE + slot;
            List<BanWaveManager.BanEntry> list = plugin.getBanWaveManager().getQueue();
            if (index < 0 || index >= list.size()) return;
            if (shift) {
                plugin.getBanWaveManager().remove(index);
                viewer.sendMessage("§c✓ Игрок удалён из очереди.");
                refresh();
            }
        }

        @Override
        protected Inventory buildInventory() {
            Inventory inv = super.buildInventory();
            inv.setItem(46, new ItemBuilder(Material.TNT)
                    .name("&c&lЗАПУСТИТЬ БАН-ВОЛНУ")
                    .lore("&7Забанить всех из очереди",
                            "&7Игроков в очереди: &c" + plugin.getBanWaveManager().getQueueSize())
                    .glowing()
                    .build());
            int slot = SLOT_MAIN_MENU;
            inv.setItem(slot, new ItemBuilder(org.bukkit.Material.NETHER_STAR).name("&c« В главное меню").build());
            inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());
            return inv;
        }
    }
}
