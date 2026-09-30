package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectedPlayersManager {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> protectedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recentlyAutoPardoned = ConcurrentHashMap.newKeySet();
    private final File dataFile;
    private volatile org.bukkit.scheduler.BukkitTask monitorTask;

    private static final Set<String> BLOCKED_BASE_COMMANDS = Set.of(
            "ban", "ban-ip", "banip", "tempban", "kick", "mute",
            "jail", "togglejail", "t", "t jailed",
            "minecraft:ban", "minecraft:ban-ip", "minecraft:kick",
            "bukkit:ban", "bukkit:kick"
    );

    private static final Set<String> BLOCKED_PREFIXES = Set.of(
            "cmi ban", "cmi kick", "cmi mute", "cmi tempban",
            "essentials ban", "essentials kick", "essentials mute",
            "essentials tempban",
            "lp user", "luckperms user"
    );

    public ProtectedPlayersManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "protected-players.yml");
        load();
    }

    public void start() {
        if (monitorTask == null) startBanMonitor();
    }

    public void stop() {
        BukkitTask task = monitorTask;
        if (task != null) task.cancel();
        monitorTask = null;
    }

    public boolean isProtected(UUID uuid) {
        return protectedPlayers.contains(uuid);
    }

    public boolean isProtected(String name) {
        for (UUID uuid : protectedPlayers) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() == null) continue;
            if (name.equalsIgnoreCase(op.getName())) return true;
        }
        return false;
    }

    public boolean addPlayer(String name) {
        if (name == null || name.isEmpty()) return false;
        OfflinePlayer target = null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            target = online;
        } else {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                String pName = p.getName();
                if (pName != null && pName.equalsIgnoreCase(name)) {
                    target = p;
                    break;
                }
            }
        }
        if (target == null || target.getUniqueId() == null) return false;
        if (protectedPlayers.contains(target.getUniqueId())) return false;
        protectedPlayers.add(target.getUniqueId());
        String targetName = target.getName();
        if (targetName != null) pardonPlayer(targetName);
        save();
        return true;
    }

    public boolean removePlayer(String name) {
        if (name == null || name.isEmpty()) return false;
        UUID found = null;
        for (UUID uuid : protectedPlayers) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String opName = op.getName();
            if (opName != null && opName.equalsIgnoreCase(name)) {
                found = uuid;
                break;
            }
        }
        if (found == null) return false;
        protectedPlayers.remove(found);
        save();
        return true;
    }

    public Set<UUID> getProtectedUUIDs() {
        return java.util.Collections.unmodifiableSet(protectedPlayers);
    }

    public String getPlayerIp(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.isOnline()) {
            Player p = op.getPlayer();
            if (p != null && p.getAddress() != null) {
                return p.getAddress().getAddress().getHostAddress();
            }
        }
        return plugin.getAltDetector().getLastIp(uuid);
    }

    public List<String> getProtectedNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : protectedPlayers) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            names.add(op.getName() != null ? op.getName() : uuid.toString().substring(0, 8));
        }
        return names;
    }

    public boolean isBlockedCommand(String command) {
        String cmd = command.toLowerCase().replaceFirst("^/", "").trim();
        String[] parts = cmd.split(" ");
        if (parts.length == 0) return false;
        String base = parts[0];

        if (BLOCKED_BASE_COMMANDS.contains(base)) return true;

        for (String prefix : BLOCKED_PREFIXES) {
            if (cmd.startsWith(prefix)) return true;
        }

        if (base.equals("amgui") || base.equals("mod") || base.equals("apanel") ||
            base.equals("adminpanel") || base.equals("sc") || base.equals("staffchat")) return false;

        return false;
    }

    public String getTargetFromCommand(String command) {
        String cmd = command.toLowerCase().replaceFirst("^/", "").trim();
        String[] parts = cmd.split(" ");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    public void pardonPlayer(String name) {
        if (name == null || name.isEmpty()) return;
        try {
            if (me.admin.gui.utils.BanService.isProfileBanned(name)) {
                me.admin.gui.utils.BanService.pardonProfile(name);
                plugin.getLogger().info("Protected player auto-pardoned (name): " + name);
            }
        } catch (Exception ignored) {}

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
            if (op == null || op.getUniqueId() == null) return;
            String ip = plugin.getAltDetector().getLastIp(op.getUniqueId());
            if (!ip.isEmpty() && me.admin.gui.utils.BanService.isIpBanned(ip)) {
                me.admin.gui.utils.BanService.pardonIp(ip);
                plugin.getLogger().info("Protected player auto-pardoned (IP): " + name);
            }
        } catch (Exception ignored) {}
    }

    private void startBanMonitor() {
        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (protectedPlayers.isEmpty()) return;
            for (UUID uuid : protectedPlayers) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String name = op.getName();
                if (name != null && me.admin.gui.utils.BanService.isProfileBanned(op)) {
                    me.admin.gui.utils.BanService.pardonProfile(op);
                    plugin.getLogger().warning("Protected player " + name + " was banned - auto-pardoned!");
                    notifyStaffBan(name, uuid);
                }
                String ip = getPlayerIp(uuid);
                if (!ip.isEmpty() && me.admin.gui.utils.BanService.isIpBanned(ip)) {
                    me.admin.gui.utils.BanService.pardonIp(ip);
                    plugin.getLogger().warning("Protected player " + name + " IP was banned - auto-pardoned (network "
                            + me.admin.gui.utils.IpPrivacyUtil.mask(ip) + ").");
                }
            }
        }, 200L, 200L);
    }

    public boolean isMonitorRunning() { return monitorTask != null; }

    private void notifyStaffBan(String name, UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            online.sendMessage("§8[§cAM§8] §aПопытка бана обнаружена и заблокирована.");
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.protect")) {
                staff.sendMessage("§8[§cAM§8] §cЗащищённый игрок §f" + name + " §cбыл забанен — авто-разбан!");
            }
        }
    }

    public void checkOnLogin(Player player) {
        if (!protectedPlayers.contains(player.getUniqueId())) return;
        boolean wasBanned = recentlyAutoPardoned.remove(player.getUniqueId());
        try {
            if (me.admin.gui.utils.BanService.isProfileBanned(player)) {
                me.admin.gui.utils.BanService.pardonProfile(player);
                wasBanned = true;
            }
        } catch (Exception ignored) {}
        try {
            String ip = getPlayerIp(player.getUniqueId());
            if (!ip.isEmpty() && me.admin.gui.utils.BanService.isIpBanned(ip)) {
                me.admin.gui.utils.BanService.pardonIp(ip);
                wasBanned = true;
            }
        } catch (Exception ignored) {}
        if (wasBanned) {
            player.sendMessage("§8[§cAM§8] §aВы были автоматически разбанены (защита).");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("amgui.protect")) {
                    staff.sendMessage("§8[§cAM§8] §c" + player.getName() + " зашёл с баном — авто-разбан!");
                }
            }
        }
    }

    public void markAutoPardoned(UUID uuid) {
        recentlyAutoPardoned.add(uuid);
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getStringList("protected")) {
            try {
                protectedPlayers.add(UUID.fromString(key));
            } catch (Exception ignored) {}
        }
        plugin.getLogger().info("Loaded " + protectedPlayers.size() + " protected players.");
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("protected", protectedPlayers.stream().map(UUID::toString).toList());
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "protected players");
    }
}
