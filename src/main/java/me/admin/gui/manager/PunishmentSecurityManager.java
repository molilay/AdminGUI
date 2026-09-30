package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PunishmentSecurityManager {
    public record Decision(boolean allowed, String reason) {
        public static Decision allow() { return new Decision(true, ""); }
        public static Decision deny(String reason) { return new Decision(false, reason); }
    }

    private static final Set<String> RATE_LIMITED = Set.of("ban", "tempban", "ipban", "kick", "freeze",
            "warn", "mute", "clear-inventory", "rollback");
    private static final Map<String, Integer> DEFAULT_WEIGHTS = Map.of(
            "ban", 5, "tempban", 5, "ipban", 5, "rollback", 4, "clear-inventory", 4,
            "kick", 2, "freeze", 2, "mute", 2, "warn", 1);

    private final AdvancedModeratorGUI plugin;
    private final WeightedRateLimiter limiter = new WeightedRateLimiter();
    private final Map<UUID, Long> anomalyAlerts = new ConcurrentHashMap<>();

    public PunishmentSecurityManager(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public Decision validate(CommandSender actor, OfflinePlayer target, String action) {
        if (!(actor instanceof Player staff)) return Decision.allow();
        String normalizedAction = action == null ? "unknown" : action.toLowerCase(Locale.ROOT);
        String bypassPermission = plugin.getConfig().getString("punishment-security.bypass-permission",
                "amgui.hierarchy.bypass");
        boolean bypass = staff.hasPermission(bypassPermission);

        // Authorization is evaluated before a successful action consumes rate budget.
        if (!bypass) {
            Decision authorization = authorize(staff, target, normalizedAction);
            if (!authorization.allowed()) return authorization;
        }

        if (RATE_LIMITED.contains(normalizedAction)) {
            int legacyLimit = Math.clamp(plugin.getConfig().getInt("punishment-security.actions-per-minute", 20), 3, 200);
            int actorLimit = Math.clamp(plugin.getConfig().getInt("punishment-security.weight-per-minute", legacyLimit), 3, 1000);
            int globalLimit = Math.clamp(plugin.getConfig().getInt("punishment-security.global-weight-per-minute", 200), 10, 5000);
            int hardCap = Math.clamp(plugin.getConfig().getInt("punishment-security.bypass-hard-cap-per-minute", 100),
                    5, 2000);
            int weight = Math.clamp(plugin.getConfig().getInt("punishment-security.action-weights." + normalizedAction,
                    DEFAULT_WEIGHTS.getOrDefault(normalizedAction, 1)), 1, 50);
            WeightedRateLimiter.Outcome outcome = limiter.acquire(staff.getUniqueId(), weight, bypass,
                    System.currentTimeMillis(), 60_000L, actorLimit, globalLimit, hardCap);
            if (outcome != WeightedRateLimiter.Outcome.ALLOWED) {
                String reason = switch (outcome) {
                    case ACTOR_LIMIT -> "превышен взвешенный лимит действий модератора";
                    case GLOBAL_LIMIT -> "сработал глобальный предохранитель массовых действий";
                    case BYPASS_HARD_CAP -> "достигнут жёсткий предел действий даже с bypass";
                    case ALLOWED -> "";
                };
                alertAnomaly(staff, target, normalizedAction, outcome, weight);
                return deny(staff, target, normalizedAction, reason);
            }
        }
        return Decision.allow();
    }

    private Decision authorize(Player staff, OfflinePlayer target, String action) {
        if (plugin.getConfig().getBoolean("punishment-security.prevent-self-action", true)
                && staff.getUniqueId().equals(target.getUniqueId())) {
            return deny(staff, target, action, "нельзя применить действие к самому себе");
        }
        if (plugin.getProtectedPlayersManager().isProtected(target.getUniqueId())) {
            return deny(staff, target, action, "игрок находится под защитой");
        }
        Player online = target.getPlayer();
        String exemption = switch (action) {
            case "ban", "tempban", "ipban" -> "amgui.ban.exempt";
            case "kick" -> "amgui.kick.exempt";
            case "freeze" -> "amgui.freeze.exempt";
            default -> "";
        };
        boolean exempt = online != null && !exemption.isEmpty() && online.hasPermission(exemption);
        if (online == null && !exemption.isEmpty()) {
            exempt = plugin.getLuckPermsIntegration().findUserPermission(target.getUniqueId(), exemption).orElse(false);
        }
        if (exempt) return deny(staff, target, action, "у игрока есть иммунитет " + exemption);

        if (plugin.getConfig().getBoolean("punishment-security.enforce-hierarchy", true)) {
            int actorWeight = plugin.getLuckPermsIntegration().getLoadedUserWeight(staff.getUniqueId());
            java.util.OptionalInt targetWeightResult = plugin.getLuckPermsIntegration().findUserWeight(target.getUniqueId());
            if (targetWeightResult.isEmpty()
                    && plugin.getConfig().getBoolean("punishment-security.fail-closed-on-lookup-error", true)) {
                return deny(staff, target, action, "не удалось безопасно проверить иерархию LuckPerms");
            }
            int targetWeight = targetWeightResult.orElse(0);
            boolean allowEqual = plugin.getConfig().getBoolean("punishment-security.allow-equal-weight", false);
            if (targetWeight > actorWeight || (!allowEqual && targetWeight == actorWeight && targetWeight > 0)) {
                return deny(staff, target, action, "уровень целевой группы не ниже уровня модератора");
            }
        }
        return Decision.allow();
    }

    public int getMassActionLimit(CommandSender actor) {
        int normal = Math.clamp(plugin.getConfig().getInt("punishment-security.mass-action-limit", 10), 1, 100);
        String bypass = plugin.getConfig().getString("punishment-security.bypass-permission", "amgui.hierarchy.bypass");
        if (!actor.hasPermission(bypass)) return normal;
        return Math.clamp(plugin.getConfig().getInt("punishment-security.bypass-mass-action-hard-cap", 100), normal, 500);
    }

    private void alertAnomaly(Player staff, OfflinePlayer target, String action,
                              WeightedRateLimiter.Outcome outcome, int weight) {
        long now = System.currentTimeMillis();
        Long previous = anomalyAlerts.put(staff.getUniqueId(), now);
        if (previous != null && now - previous < 10_000L) return;
        String details = "actor=" + staff.getName() + "; target=" + target.getName() + "; action=" + action
                + "; outcome=" + outcome + "; weight=" + weight;
        plugin.getAuditManager().record(staff, "punishment.rate-anomaly", target.getName(), target.getUniqueId(), details);
        Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission("amgui.admin"))
                .forEach(player -> player.sendMessage("§8[§cSecurity§8] §cАномальная серия действий §f"
                        + staff.getName() + "§c: §7" + outcome));
        plugin.getDiscordWebhook().ifPresent(webhook -> webhook.send("security-alert", target.getName(),
                staff.getName(), "Punishment circuit breaker: " + outcome, action));
    }

    private Decision deny(Player staff, OfflinePlayer target, String action, String reason) {
        plugin.getAuditManager().record(staff, "action.denied." + action, target.getName(), target.getUniqueId(), reason);
        return Decision.deny(reason);
    }
}
