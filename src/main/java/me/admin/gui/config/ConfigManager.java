package me.admin.gui.config;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

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

    public void adoptCurrent() {
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
        if (plugin.getLocalizationManager() != null
                && plugin.getLocalizationManager().contains("messages." + path)) {
            return plugin.getLocalizationManager().get("messages." + path, "&cMessage not found: " + path);
        }
        String configValue = config.getString("messages." + path);
        if (configValue != null) return configValue.replace("&", "§");
        return ("&cMessage not found: " + path).replace("&", "§");
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
        plugin.getServer().broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    public boolean needsConfirmation(String action) {
        return config.getBoolean("confirmations." + action, true);
    }

    public String getThemeColor(String key) {
        return config.getString("theme." + key, "&7").replace("&", "§");
    }

    public int getWarnPointsThreshold() { return plugin.getConfig().getInt("warn.points-threshold", 5); }
    public boolean isWarnAutoBan() { return plugin.getConfig().getBoolean("warn.auto-ban", true); }
    public int getWarnDefaultPoints() { return plugin.getConfig().getInt("warn.default-points", 1); }

    public boolean isBlacklistEnabled() { return plugin.getConfig().getBoolean("blacklist.enabled", true); }
    public String getBlacklistAction() { return plugin.getConfig().getString("blacklist.action", "kick"); }
    public String getBlacklistReason() { return plugin.getConfig().getString("blacklist.reason", "Использование запрещённых слов"); }

    public int getItemsPerPage() { return plugin.getConfig().getInt("gui.items-per-page", 45); }

    public String translateColors(String text) {
        return text.replace("&", "§");
    }

    public String getPunishSound(String action) {
        return config.getString("notifications." + action + ".sound", "BLOCK_NOTE_BLOCK_PLING");
    }

    public String getPunishTitle(String action) {
        return config.getString("notifications." + action + ".title.text", "&c" + action.toUpperCase());
    }

    public String getPunishSubtitle(String action) {
        return config.getString("notifications." + action + ".title.subtitle", "");
    }

    public void playPunishmentSound(Player player, String action) {
        String sound = getPunishSound(action);
        try {
            String key = sound.contains(":") ? sound.toLowerCase(java.util.Locale.ROOT)
                    : "minecraft:" + sound.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
            player.playSound(player.getLocation(), key, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    public void sendPunishmentTitle(Player player, String action, String target) {
        boolean enabled = config.getBoolean("notifications." + action + ".title.enabled", true);
        if (!enabled) return;
        String titleMsg = getPunishTitle(action)
            .replace("&", "§").replace("{target}", target).replace("{player}", player.getName());
        String subtitleMsg = getPunishSubtitle(action)
            .replace("&", "§").replace("{target}", target).replace("{player}", player.getName());
        var serializer = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        var times = net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(3500), java.time.Duration.ofMillis(1000));
        player.showTitle(net.kyori.adventure.title.Title.title(
                serializer.deserialize(titleMsg), serializer.deserialize(subtitleMsg), times));
    }
}
