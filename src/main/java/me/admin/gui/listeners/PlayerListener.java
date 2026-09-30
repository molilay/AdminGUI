package me.admin.gui.listeners;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerListener implements Listener {

    private final AdvancedModeratorGUI plugin;
    private final AtomicBoolean reputationWarningSent = new AtomicBoolean();

    public PlayerListener(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getFreezeManager().checkFrozenOnJoin(player);

        String ip = player.getAddress() != null ?
                player.getAddress().getAddress().getHostAddress() : "unknown";
        plugin.getAltDetector().recordLogin(player.getUniqueId(), player.getName(), ip);
        plugin.getAntiRaidManager().onJoin(player, ip);
        if (plugin.getAntiRaidManager().shouldBlockJoin(player)) {
            player.kick(me.admin.gui.utils.TextUtil.legacy("§cСервер временно ограничил вход новых игроков из-за рейда. Попробуйте позже."));
            return;
        }

        if (player.hasPermission("amgui.staffchat")) {
            plugin.getStaffChatManager().notifyJoin(player);
        }

        plugin.getInvestigationManager().onPlayerJoin(player);

        checkAntiVpn(player, ip);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getGuiManager().cleanup(player.getUniqueId());

        plugin.getPlayerInventoryCache().cache(player);

        plugin.getInvestigationManager().onPlayerQuit(player);

        if (player.hasPermission("amgui.staffchat")) {
            plugin.getStaffChatManager().notifyLeave(player);
        }
        plugin.getStaffChatManager().toggleCleanup(player);
        plugin.getAutoModManager().clearPlayerData(player.getUniqueId());
        if (plugin.getModerationSessionManager().isActive(player)) {
            plugin.getModerationSessionManager().finish(player, "Сессия завершена автоматически: модератор вышел");
        }
    }

    private void checkAntiVpn(Player player, String ip) {
        if (!plugin.getConfig().getBoolean("antivpn.enabled", false)) return;
        if (player.hasPermission("amgui.admin")) return;

        plugin.getGeoIpManager().lookup(ip).whenComplete((result, failure) -> {
            if (failure != null || result == null || !plugin.isEnabled()) return;
            boolean known = result.proxy() != null || result.vpn() != null || result.tor() != null || result.hosting() != null;
            if (!known) {
                if (reputationWarningSent.compareAndSet(false, true)) {
                    plugin.getLogger().warning("AntiVPN включён, но GeoIP endpoint не вернул security-поля. "
                            + "Настройте HTTPS-провайдер с proxy/vpn/tor/hosting; небезопасный HTTP fallback отключён.");
                }
                return;
            }
            boolean suspicious = Boolean.TRUE.equals(result.proxy()) || Boolean.TRUE.equals(result.vpn())
                    || Boolean.TRUE.equals(result.tor()) || Boolean.TRUE.equals(result.hosting());
            if (!suspicious) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                for (Player staff : plugin.getServer().getOnlinePlayers()) {
                    if (!staff.hasPermission("amgui.admin")) continue;
                    String shownIp = staff.hasPermission("amgui.viewip") ? ip : me.admin.gui.utils.IpPrivacyUtil.mask(ip);
                    staff.sendMessage("§8[§cVPN§8] §f" + player.getName() + " §7подозрительный IP: §f" + shownIp);
                }
                String discordDetails = plugin.getConfig().getBoolean("discord.include-sensitive-ip", false)
                        ? "VPN/Proxy detected: " + ip
                        : "VPN/Proxy detected: " + me.admin.gui.utils.IpPrivacyUtil.mask(ip);
                plugin.getDiscordWebhook().ifPresent(webhook -> webhook.send("vpn", player.getName(), "AntiVPN", discordDetails, "—"));
                plugin.getAuditManager().record(null, "AntiVPN", "antivpn.detect", player.getName(), player.getUniqueId(),
                        "proxy=" + result.proxy() + "; vpn=" + result.vpn() + "; tor=" + result.tor() + "; hosting=" + result.hosting());
                if (plugin.getConfig().getBoolean("antivpn.block-on-vpn", false)) {
                    player.kick(me.admin.gui.utils.TextUtil.legacy("§cVPN/Proxy не разрешён на сервере."));
                }
            });
        });
    }
}
