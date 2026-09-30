package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrialModerationManager {

    private final AdvancedModeratorGUI plugin;
    private final Set<UUID> trialModerators = ConcurrentHashMap.newKeySet();
    private final Map<Integer, PendingAction> pendingActions = new LinkedHashMap<>();
    private final File dataFile;
    private int nextId = 1;

    public record PendingAction(int id, UUID trialId, String trialName, String type, String target, String reason, long duration, long timestamp) {}

    public TrialModerationManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "trialmod.yml");
        load();
    }

    public boolean isTrialModerator(Player player) {
        return trialModerators.contains(player.getUniqueId());
    }

    public void setTrialModerator(Player player, boolean value) {
        if (value) trialModerators.add(player.getUniqueId());
        else trialModerators.remove(player.getUniqueId());
        save();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("trial-moderation.enabled", false);
    }

    public PendingAction requestAction(Player trial, String type, String target, String reason, long duration) {
        org.bukkit.OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(target);
        PunishmentSecurityManager.Decision decision = plugin.getPunishmentSecurityManager()
                .validate(trial, targetPlayer, type);
        if (!decision.allowed()) {
            trial.sendMessage("§cДействие запрещено: " + decision.reason());
            return null;
        }
        if (!isEnabled()) {
            executeDirect(trial, type, target, reason, duration);
            return null;
        }
        PendingAction action = new PendingAction(nextId++, trial.getUniqueId(), trial.getName(), type, target, reason, duration, System.currentTimeMillis());
        pendingActions.put(action.id(), action);

        String msg = "§8[§6Trial§8] §f" + trial.getName() + " §eзапросил §f" + type + " §eдля §f" + target + "§e:\n §7" + reason
                + " §8(§7#" + action.id() + "§8)\n §8▶ §7/amgui trialconfirm " + action.id();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.trial.confirm")) {
                staff.sendMessage(msg);
            }
        }
        trial.sendMessage("§e✓ Запрос на §f" + type + " §eотправлен на подтверждение старшим.");
        save();
        plugin.getStaffActivityManager().ifPresent(s -> s.log(trial, "TrialRequest", target, type + ": " + reason, duration));
        return action;
    }

    public void confirmAction(int id, Player confirmer) {
        PendingAction action = pendingActions.get(id);
        if (action == null) { confirmer.sendMessage("§cЗапрос #" + id + " не найден."); return; }
        PunishmentSecurityManager.Decision decision = plugin.getPunishmentSecurityManager()
                .validate(confirmer, Bukkit.getOfflinePlayer(action.target()), action.type());
        if (!decision.allowed()) {
            confirmer.sendMessage("§cДействие запрещено: " + decision.reason());
            return;
        }
        pendingActions.remove(id);
        Player trial = Bukkit.getPlayer(action.trialId());
        executeDirect(confirmer, action.type(), action.target(), action.reason(), action.duration());
        var confirmation = me.admin.gui.utils.TextUtil.legacy("§8[§6Trial§8] §aСтарший §f" + confirmer.getName()
                + " §aподтвердил §f" + action.type() + " §aдля §f" + action.target());
        Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission("amgui.trial.confirm"))
                .forEach(player -> player.sendMessage(confirmation));
        Bukkit.getConsoleSender().sendMessage(confirmation);
        save();
    }

    public void denyAction(int id, Player confirmer) {
        PendingAction action = pendingActions.get(id);
        if (action == null) { confirmer.sendMessage("§cЗапрос #" + id + " не найден."); return; }
        pendingActions.remove(id);
        Player trial = Bukkit.getPlayer(action.trialId());
        if (trial != null && trial.isOnline()) {
            trial.sendMessage("§cВаш запрос на " + action.type() + " для " + action.target() + " отклонён старшим.");
        }
        confirmer.sendMessage("§c✓ Запрос #" + id + " отклонён.");
        save();
    }

    public List<PendingAction> getPendingActions() {
        return pendingActions.values().stream().sorted(Comparator.comparingInt(PendingAction::id)).toList();
    }

    private void executeDirect(Player who, String type, String target, String reason, long duration) {
        switch (type) {
            case "ban" -> {
                me.admin.gui.utils.BanService.banProfile(target, reason, duration > 0
                        ? java.time.Instant.ofEpochMilli(System.currentTimeMillis() + duration * 1000)
                        : null, who.getName());
                Player p = Bukkit.getPlayerExact(target);
                if (p != null) p.kick(me.admin.gui.utils.TextUtil.legacy("§cВы забанены.\n§7Причина: " + reason));
                plugin.getDatabaseManager().logPunishment(duration > 0 ? "tempban" : "ban", who.getName(), target, reason, duration);
            }
            case "mute" -> {
                Player p = Bukkit.getPlayerExact(target);
                if (p != null) plugin.getMuteManager().mute(p, reason, who.getName(), duration);
            }
            case "kick" -> {
                Player p = Bukkit.getPlayerExact(target);
                if (p != null) p.kick(me.admin.gui.utils.TextUtil.legacy("§cКикнуты.\n§7Причина: " + reason));
                plugin.getDatabaseManager().logPunishment("kick", who.getName(), target, reason, -1);
            }
            case "warn" -> {
                Player p = Bukkit.getPlayerExact(target);
                if (p != null) plugin.getWarnManager().warn(p, reason, who.getName());
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId);
        List<String> list = trialModerators.stream().map(UUID::toString).toList();
        config.set("trial-moderators", list);
        YamlPersistenceService.queueYaml(plugin, dataFile, config, "trial moderation");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        nextId = config.getInt("next-id", 1);
        for (String s : config.getStringList("trial-moderators")) {
            try { trialModerators.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }
}
