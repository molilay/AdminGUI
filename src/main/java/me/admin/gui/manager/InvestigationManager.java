package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvestigationManager {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> investigating = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> watchList = new ConcurrentHashMap<>();
    private final File dataFile;

    public InvestigationManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "investigations.yml");
        load();
    }

    public void toggleInvestigator(Player staff) {
        if (investigating.contains(staff.getUniqueId())) {
            investigating.remove(staff.getUniqueId());
            watchList.remove(staff.getUniqueId());
            staff.sendMessage("§cРежим расследования выключен.");
        } else {
            investigating.add(staff.getUniqueId());
            watchList.put(staff.getUniqueId(), ConcurrentHashMap.newKeySet());
            staff.sendMessage("§aРежим расследования включён. Используйте /mod investigate <игрок> для слежки.");
        }
        save();
    }

    public boolean isInvestigating(Player staff) {
        return investigating.contains(staff.getUniqueId());
    }

    public Set<UUID> getWatchedPlayers(Player staff) {
        return watchList.getOrDefault(staff.getUniqueId(), Collections.emptySet());
    }

    public void watch(Player staff, String targetName) {
        if (!investigating.contains(staff.getUniqueId())) {
            staff.sendMessage("§cСначала включите режим расследования: /mod investigate");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            staff.sendMessage("§cИгрок не найден.");
            return;
        }
        watchList.computeIfAbsent(staff.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(target.getUniqueId());
        staff.sendMessage("§a✓ Вы начали слежку за §f" + targetName);
        save();
    }

    public void unwatch(Player staff, String targetName) {
        Set<UUID> watched = watchList.get(staff.getUniqueId());
        if (watched == null) return;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return;
        watched.remove(target.getUniqueId());
        staff.sendMessage("§cВы остановили слежку за §f" + targetName);
        save();
    }

    public void onPlayerQuit(Player player) {
        UUID pid = player.getUniqueId();
        boolean beingWatched = false;
        for (Map.Entry<UUID, Set<UUID>> entry : watchList.entrySet()) {
            if (entry.getValue().contains(pid)) {
                beingWatched = true;
                Player staff = Bukkit.getPlayer(entry.getKey());
                if (staff != null && staff.isOnline()) {
                    sendInvestigationSummary(staff, player);
                }
            }
        }
        if (beingWatched) {
            plugin.getInventoryRollbackManager().saveSnapshot(player, "investigation_quit");
        }
    }

    public void onPlayerJoin(Player player) {
        UUID pid = player.getUniqueId();
        for (Map.Entry<UUID, Set<UUID>> entry : watchList.entrySet()) {
            if (entry.getValue().contains(pid)) {
                Player staff = Bukkit.getPlayer(entry.getKey());
                if (staff != null && staff.isOnline()) {
                    staff.sendMessage("§8[§6Invest§8] §a" + player.getName() + " §eзашёл на сервер!");
                    staff.sendMessage(" §7Мир: §f" + player.getWorld().getName() +
                            " §7| Коорд: §f" + (int)player.getLocation().getX() +
                            " " + (int)player.getLocation().getY() +
                            " " + (int)player.getLocation().getZ());
                    String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "?";
                    staff.sendMessage(" §7IP: §f" + displayIp(staff, ip));
                    plugin.getInventoryRollbackManager().saveSnapshot(player, "investigation_join");
                }
            }
        }
    }

    private void sendInvestigationSummary(Player staff, Player target) {
        staff.sendMessage("§8╔═══════════════════════════════");
        staff.sendMessage("§8║ §6Отчёт по §f" + target.getName());
        staff.sendMessage("§8║ §7Вышел из игры");
        Location loc = target.getLocation();
        staff.sendMessage("§8║ §7Последние коорд: §f" + (int)loc.getX() + " " + (int)loc.getY() + " " + (int)loc.getZ() + " §8(§7" + loc.getWorld().getName() + "§8)");
        String ip = target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "?";
        staff.sendMessage("§8║ §7IP: §f" + displayIp(staff, ip));
        staff.sendMessage("§8║ §7Инвентарь сохранён для отката");
        staff.sendMessage("§8╚═══════════════════════════════");
    }

    public List<String> getWatchedPlayerNames(Player staff) {
        List<String> names = new ArrayList<>();
        for (UUID uid : getWatchedPlayers(staff)) {
            Player p = Bukkit.getPlayer(uid);
            names.add(p != null ? p.getName() : "? (" + uid.toString().substring(0, 8) + ")");
        }
        return names;
    }

    private static String displayIp(Player staff, String ip) {
        return staff.hasPermission("amgui.viewip") ? ip : me.admin.gui.utils.IpPrivacyUtil.mask(ip);
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (UUID staffId : investigating) {
            config.set(staffId + ".enabled", true);
            Set<UUID> watched = watchList.get(staffId);
            if (watched != null) {
                List<String> list = watched.stream().map(UUID::toString).toList();
                config.set(staffId + ".watching", list);
            }
        }
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "investigations");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID staffId = UUID.fromString(key);
                investigating.add(staffId);
                Set<UUID> watched = ConcurrentHashMap.newKeySet();
                for (String uidStr : config.getStringList(key + ".watching")) {
                    try { watched.add(UUID.fromString(uidStr)); } catch (Exception ignored) {}
                }
                watchList.put(staffId, watched);
            } catch (Exception ignored) {}
        }
    }
}
