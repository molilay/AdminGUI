package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.YamlPersistenceService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.util.*;

public class LoginAlertListener implements Listener {

    private final AdvancedModeratorGUI plugin;
    private final Map<String, String> knownIps = new HashMap<>();
    private final File dataFile;

    public LoginAlertListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "known-ips.yml");
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        Map<String, Object> raw = config.getValues(false);
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                knownIps.put(e.getKey().toLowerCase(), e.getValue().toString());
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, String> e : knownIps.entrySet()) {
            config.set(e.getKey(), e.getValue());
        }
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "known login IPs");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("login-alerts.enabled", true)) return;
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";

        String lastIp = knownIps.get(name);

        if (lastIp != null && !lastIp.equals(ip)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("amgui.alert")) {
                    boolean raw = staff.hasPermission("amgui.viewip");
                    String current = raw ? ip : me.admin.gui.utils.IpPrivacyUtil.mask(ip);
                    String previous = raw ? lastIp : me.admin.gui.utils.IpPrivacyUtil.mask(lastIp);
                    staff.sendMessage("§8[§6Alert§8] §f" + player.getName()
                            + " §eзашёл с нового IP: §f" + current + " §8(был: " + previous + ")");
                }
            }
            String discordCurrent = plugin.getConfig().getBoolean("discord.include-sensitive-ip", false)
                    ? ip : me.admin.gui.utils.IpPrivacyUtil.mask(ip);
            String discordPrevious = plugin.getConfig().getBoolean("discord.include-sensitive-ip", false)
                    ? lastIp : me.admin.gui.utils.IpPrivacyUtil.mask(lastIp);
            plugin.getDiscordWebhook().ifPresent(w ->
                    w.send("alert", player.getName(), "LoginAlert",
                            "Новый IP: " + discordCurrent + " (был: " + discordPrevious + ")", "—"));
            plugin.getAuditManager().record(null, "LoginAlert", "ip.changed", player.getName(), player.getUniqueId(),
                    "current=" + me.admin.gui.utils.IpPrivacyUtil.mask(ip)
                            + "; previous=" + me.admin.gui.utils.IpPrivacyUtil.mask(lastIp));
        }

        if (!ip.equals(knownIps.get(name))) {
            knownIps.put(name, ip);
            save();
        }
    }
}
