package me.admin.gui.config;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final AdvancedModeratorGUI plugin;
    private FileConfiguration config;

    public ConfigManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public List<String> getKickReasons() {
        return config.getStringList("reasons.kick");
    }

    public List<String> getBanReasons() {
        return config.getStringList("reasons.ban");
    }

    public List<String> getTempbanDurations() {
        return config.getStringList("durations.tempban");
    }

    public List<String> getTempGroupDurations() {
        return config.getStringList("durations.tempgroup");
    }

    public List<String> getMuteDurations() {
        return config.getStringList("durations.mute");
    }

    public List<String> getWarnReasons() {
        return config.getStringList("reasons.warn");
    }

    public String getMessage(String path) {
        String msg = config.getString("messages." + path, "&cMessage not found: " + path);
        return msg.replace("&", "§");
    }

    public String getFormattedMessage(String path, String... replacements) {
        String msg = getMessage(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                msg = msg.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        return msg;
    }

    public String getGuiTitle(String path) {
        String title = config.getString("gui." + path, "&8Title");
        return title.replace("&", "§");
    }

    public String getGuiTitle(String path, String... replacements) {
        String title = getGuiTitle(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                title = title.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        return title;
    }

    public String getPrefix() {
        return getMessage("prefix");
    }

    public boolean isFreezeBlind() { return config.getBoolean("freeze.blind-effect", true); }
    public boolean isFreezeSlow() { return config.getBoolean("freeze.slow-effect", true); }
    public boolean isFreezeChat() { return config.getBoolean("freeze.disable-chat", true); }
    public boolean isFreezeCommands() { return config.getBoolean("freeze.disable-commands", true); }
    public boolean isFreezeInteract() { return config.getBoolean("freeze.disable-interact", true); }

    public boolean shouldBroadcast(String type) {
        return config.getBoolean("broadcast." + type, false);
    }

    public String getSound(String key) {
        return config.getString("sounds." + key, "UI_BUTTON_CLICK");
    }

    public void broadcastPunishment(String type, String player, String reason) {
        if (!shouldBroadcast(type)) return;
        String msg = getFormattedMessage("broadcast-" + type, "player", player, "reason", reason);
        if (msg.contains("Message not found")) {
            plugin.getLogger().warning("Broadcast message not found: broadcast-" + type);
            return;
        }
        plugin.getServer().broadcastMessage(msg);
    }
}
