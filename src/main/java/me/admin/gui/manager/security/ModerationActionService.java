package me.admin.gui.manager.security;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.manager.PunishmentSecurityManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Single authorization entry point for moderator actions. Call {@link #execute}
 * immediately before mutating player/server state; GUI checks are only previews.
 */
public final class ModerationActionService {

    public enum Action {
        KICK("amgui.kick", "kick"),
        BAN("amgui.ban", "ban"),
        TEMPBAN("amgui.ban", "tempban"),
        IP_BAN("amgui.ban", "ipban"),
        FREEZE("amgui.freeze", "freeze"),
        WARN("amgui.warn", "warn"),
        MUTE("amgui.mute", "mute"),
        TELEPORT("amgui.teleport", "teleport"),
        INVENTORY_EDIT("amgui.inventory.edit", "clear-inventory"),
        AUTHME("amgui.authme.sensitive", "authme"),
        UNDO("amgui.action.undo", "undo");

        private final String permission;
        private final String securityAction;

        Action(String permission, String securityAction) {
            this.permission = permission;
            this.securityAction = securityAction;
        }

        public String permission() { return permission; }
        public String securityAction() { return securityAction; }

        public static Action fromSecurityAction(String input) {
            if (input == null) return null;
            String normalized = input.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "kick" -> KICK;
                case "ban" -> BAN;
                case "tempban" -> TEMPBAN;
                case "ipban" -> IP_BAN;
                case "freeze" -> FREEZE;
                case "warn" -> WARN;
                case "mute" -> MUTE;
                case "teleport" -> TELEPORT;
                case "inventory-edit", "inventory-clear", "clear-inventory" -> INVENTORY_EDIT;
                case "authme" -> AUTHME;
                case "undo" -> UNDO;
                default -> null;
            };
        }
    }

    public record Decision(boolean allowed, String reason) {
        public static Decision allow() { return new Decision(true, ""); }
        public static Decision deny(String reason) { return new Decision(false, reason); }
    }

    private ModerationActionService() {}

    /** Cheap UI check. It deliberately does not consume the punishment rate budget. */
    public static Decision preview(AdvancedModeratorGUI plugin, Player actor,
                                   OfflinePlayer target, Action action) {
        Decision permission = checkPermission(plugin, actor, target, action);
        if (!permission.allowed()) return permission;
        return hardTargetConstraints(plugin, actor, target, action, false);
    }

    /** Full fail-closed preflight, intended to be the last call before the mutation. */
    public static Decision execute(AdvancedModeratorGUI plugin, Player actor,
                                   OfflinePlayer target, Action action) {
        Decision permission = checkPermission(plugin, actor, target, action);
        if (!permission.allowed()) return permission;
        Decision targetConstraints = hardTargetConstraints(plugin, actor, target, action, true);
        if (!targetConstraints.allowed()) return targetConstraints;
        if (!plugin.getAuditManager().isAvailableForCriticalActions()) {
            return Decision.deny("защищённый аудит недоступен; действие отклонено");
        }
        PunishmentSecurityManager.Decision security = plugin.getPunishmentSecurityManager()
                .validate(actor, target, action.securityAction());
        if (!security.allowed()) return Decision.deny(security.reason());
        boolean journaled = plugin.getAuditManager().tryRecord(actor.getUniqueId(), actor.getName(),
                "action.preflight-approved." + action.securityAction(), target.getName(), target.getUniqueId(),
                "permission=" + action.permission());
        return journaled ? Decision.allow()
                : Decision.deny("не удалось зарезервировать запись защищённого аудита");
    }

    private static Decision checkPermission(AdvancedModeratorGUI plugin, Player actor,
                                            OfflinePlayer target, Action action) {
        if (actor == null || !actor.isOnline()) {
            return Decision.deny("инициатор больше не находится на сервере");
        }
        if (!actor.hasPermission(action.permission())) {
            return denied(plugin, actor, target, action,
                    "отозвано или отсутствует право " + action.permission());
        }
        return Decision.allow();
    }

    private static Decision hardTargetConstraints(AdvancedModeratorGUI plugin, Player actor,
                                                   OfflinePlayer target, Action action,
                                                   boolean failClosedOfflineLookup) {
        if (actor.getUniqueId().equals(target.getUniqueId())) {
            return denied(plugin, actor, target, action, "нельзя применить действие к самому себе");
        }
        if (plugin.getProtectedPlayersManager().isProtected(target.getUniqueId())) {
            return denied(plugin, actor, target, action, "игрок находится под защитой");
        }
        String exemption = switch (action) {
            case BAN, TEMPBAN, IP_BAN -> "amgui.ban.exempt";
            case KICK -> "amgui.kick.exempt";
            case FREEZE -> "amgui.freeze.exempt";
            default -> "";
        };
        if (exemption.isEmpty()) return Decision.allow();
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            return online.hasPermission(exemption)
                    ? denied(plugin, actor, target, action, "у игрока есть иммунитет " + exemption)
                    : Decision.allow();
        }
        if (!failClosedOfflineLookup) return Decision.allow();
        var exempt = plugin.getLuckPermsIntegration().findUserPermission(target.getUniqueId(), exemption);
        if (exempt.isEmpty()) {
            return Decision.deny("не удалось безопасно проверить иммунитет " + exemption);
        }
        return exempt.get()
                ? denied(plugin, actor, target, action, "у игрока есть иммунитет " + exemption)
                : Decision.allow();
    }

    private static Decision denied(AdvancedModeratorGUI plugin, Player actor,
                                   OfflinePlayer target, Action action, String reason) {
        plugin.getAuditManager().record(actor, "action.denied." + action.securityAction(),
                target.getName(), target.getUniqueId(), reason);
        return Decision.deny(reason);
    }
}
