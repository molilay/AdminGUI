package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.ProtectedPlayersManager;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

public class ProtectionListener implements Listener {

    private final AdvancedModeratorGUI plugin;

    private static final String CANCEL_MSG = "§8[§cAM§8] §cЭтот игрок защищён от наказаний.";

    public ProtectionListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        String message = event.getMessage();
        ProtectedPlayersManager ppm = plugin.getProtectedPlayersManager();

        if (!ppm.isBlockedCommand(message)) return;

        String targetName = ppm.getTargetFromCommand(message);
        if (targetName == null) return;

        if (ppm.isProtected(targetName)) {
            event.setCancelled(true);
            sender.sendMessage(CANCEL_MSG);
            plugin.getLogger().warning("Blocked command from " + sender.getName() +
                    " targeting protected player " + targetName + ": " + message);
            plugin.getAuditManager().record(sender, "protection.command-blocked", targetName, null,
                    "command=" + message);
            boolean punished = plugin.getConfig().getBoolean("punishment-security.punish-protection-violations", false);
            if (punished) {
                punishOffender(sender, targetName, message);
            }
            notifyAdmins(sender.getName(), targetName, message, punished);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!plugin.getProtectedPlayersManager().isProtected(victim.getUniqueId())) return;

        event.setCancelled(true);

        if (event.getDamager() instanceof Player attacker) {
            attacker.sendMessage("§8[§cAM§8] §cНельзя атаковать защищённого игрока!");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("amgui.protect") && !staff.equals(attacker)) {
                    staff.sendMessage("§8[§cAM§8] §c" + attacker.getName() + " пытался ударить защищённого §f" + victim.getName());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getProtectedPlayersManager().checkOnLogin(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (plugin.getProtectedPlayersManager().isProtected(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getLogger().warning("Blocked kick of protected player " + player.getName());
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("amgui.protect")) {
                    staff.sendMessage("§8[§cAM§8] §cКик защищённого игрока §f" + player.getName() + " §cзаблокирован!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getProtectedPlayersManager().isProtected(event.getUniqueId())) return;
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {
            event.allow();
            UUID uuid = event.getUniqueId();
            String name = event.getName();
            plugin.getProtectedPlayersManager().markAutoPardoned(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getProtectedPlayersManager().pardonPlayer(name);
                plugin.getAuditManager().record(null, "System", "protection.auto-pardon", name, uuid,
                        "pre-login ban overridden");
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("amgui.protect")) {
                        staff.sendMessage("§8[§cAM§8] §c⚡ " + name + " пытался зайти с баном — авто-разбан!");
                    }
                }
            });
        }
    }

    private void punishOffender(Player offender, String targetName, String command) {
        try {
            String offenderName = offender.getName();

            if (offender.isOp()) {
                offender.setOp(false);
                plugin.getLogger().warning("Снят OP с " + offenderName + " за попытку наказать защищённого " + targetName);
            }

            try {
                net.luckperms.api.model.group.Group defaultGroup = plugin.getLuckPermsIntegration().getGroup("default");
                if (defaultGroup != null) {
                    plugin.getLuckPermsIntegration().setGroup(offender, defaultGroup);
                    plugin.getLogger().warning("Группа " + offenderName + " сброшена на default за попытку наказать защищённого " + targetName);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Не удалось сбросить группу " + offenderName + ": " + e.getMessage());
            }

            offender.kick(me.admin.gui.utils.TextUtil.legacy("§8[§cAM§8]\n§cВы пытались наказать защищённого игрока §f" + targetName + "\n§cOP снят, группа сброшена."));
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при наказании " + offender.getName() + ": " + e.getMessage());
        }
    }

    private void notifyAdmins(String senderName, String targetName, String command, boolean punished) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("amgui.protect") && !staff.getName().equals(senderName)) {
                staff.sendMessage("§8[§cAM§8] §cПопытка наказать защищённого игрока:");
                staff.sendMessage(" §7" + senderName + " §8→ §f" + targetName);
                staff.sendMessage(" §7Команда: §f" + command);
                staff.sendMessage(punished
                        ? " §c⚠ " + senderName + " лишён OP и сброшен на default!"
                        : " §e⚠ Команда заблокирована и записана в аудит.");
            }
        }
    }
}
