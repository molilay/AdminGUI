package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private File frozenFile;

    public FreezeManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.frozenFile = new File(plugin.getDataFolder(), "frozen-players.yml");
    }

    public void freeze(Player player) {
        frozenPlayers.add(player.getUniqueId());
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        if (plugin.getConfigManager().isFreezeBlind()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, -1, 1, false, false));
        }
        if (plugin.getConfigManager().isFreezeSlow()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, -1, 255, false, false));
        }
        player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§c❄ ЗАМОРОЖЕН"),
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§7Напишите модераторам в ЛС"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofMillis(999999 * 50L),
                        java.time.Duration.ofMillis(500)
                )));
        player.sendMessage(plugin.getConfigManager().getMessage("frozen"));
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("amgui.staffchat") && p.hasPermission("amgui.freeze"))
            .forEach(p -> {
                plugin.getConfigManager().sendPunishmentTitle(p, "freeze", player.getName());
                plugin.getConfigManager().playPunishmentSound(p, "freeze");
            });
        saveFrozenPlayers();
    }

    public void unfreeze(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.resetTitle();
        player.sendMessage(plugin.getConfigManager().getMessage("unfrozen"));
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("amgui.staffchat") && p.hasPermission("amgui.freeze"))
            .forEach(p -> {
                plugin.getConfigManager().sendPunishmentTitle(p, "unfreeze", player.getName());
                plugin.getConfigManager().playPunishmentSound(p, "unfreeze");
            });
        saveFrozenPlayers();
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.contains(playerId);
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    public void checkFrozenOnJoin(Player player) {
        if (frozenPlayers.contains(player.getUniqueId())) {
            freeze(player);
        }
    }

    public void unfreezeAll() {
        for (UUID id : frozenPlayers) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) {
                p.setWalkSpeed(0.2f);
                p.setFlySpeed(0.1f);
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.resetTitle();
            }
        }
        frozenPlayers.clear();
        saveFrozenPlayers();
    }

    public void saveFrozenPlayers() {
        YamlConfiguration config = new YamlConfiguration();
        for (UUID id : frozenPlayers) {
            config.set(id.toString(), true);
        }
        YamlPersistenceService.queueYaml(plugin, frozenFile, config, "frozen players");
    }

    public void loadFrozenPlayers() {
        if (!frozenFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(frozenFile);
        for (String key : config.getKeys(false)) {
            try {
                frozenPlayers.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public Set<UUID> getFrozenPlayers() {
        return frozenPlayers;
    }
}
