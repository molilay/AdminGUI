package me.admin.gui.utils;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Single source of truth for role-aware GUI and command visibility. */
public final class CapabilityRegistry {
    public enum Match { ANY, ALL }
    public enum Capability {
        PLAYER("amgui.player"), GROUPS("amgui.groups"), PROTECT("amgui.protect"),
        INBOX("amgui.inbox.view"), INCIDENT("amgui.incident.view"), REPORTS("amgui.report.staff"),
        CASES("amgui.cases"), APPEALS("amgui.appeal.staff"), AUDIT("amgui.audit"),
        INVESTIGATION("amgui.investigation.center"), BAN("amgui.ban"), MUTE("amgui.mute"),
        IP_BANS(Match.ALL, "amgui.ban", "amgui.viewip"), TEMPLATES("amgui.templates"), LOGS("amgui.logs"),
        SIMULATOR("amgui.simulator"), ANTIRAID("amgui.antiraid"), AUTOMOD("amgui.automod.view"),
        DOCTOR("amgui.doctor"), ADMIN("amgui.admin"),
        DASH_PLAYERS(Match.ANY, "amgui.player", "amgui.groups", "amgui.protect"),
        DASH_INVESTIGATIONS(Match.ANY, "amgui.investigation.center", "amgui.cases", "amgui.report.staff",
                "amgui.appeal.staff", "amgui.audit", "amgui.inbox.view", "amgui.incident.view"),
        DASH_PUNISHMENTS(Match.ANY, "amgui.ban", "amgui.mute", "amgui.warn", "amgui.templates", "amgui.logs"),
        DASH_SECURITY(Match.ANY, "amgui.antiraid", "amgui.automod.view", "amgui.audit", "amgui.simulator", "amgui.protect"),
        DASH_SYSTEM(Match.ANY, "amgui.doctor", "amgui.logs", "amgui.audit", "amgui.admin"),
        BACKUP_ANY(Match.ANY, "amgui.backup", "amgui.backup.create", "amgui.backup.restore", "amgui.backup.verify");

        private final Match match;
        private final List<String> permissions;
        Capability(String... permissions) { this(Match.ALL, permissions); }
        Capability(Match match, String... permissions) { this.match = match; this.permissions = List.of(permissions); }
        public boolean allows(CommandSender sender) {
            return match == Match.ALL ? permissions.stream().allMatch(sender::hasPermission)
                    : permissions.stream().anyMatch(sender::hasPermission);
        }
    }

    private static final Map<String, Predicate<CommandSender>> ADMIN_COMMANDS = new LinkedHashMap<>();
    private static final Map<String, Predicate<CommandSender>> MOD_COMMANDS = new LinkedHashMap<>();
    static {
        ADMIN_COMMANDS.put("reload", Capability.ADMIN::allows); ADMIN_COMMANDS.put("check", Capability.ADMIN::allows);
        ADMIN_COMMANDS.put("announce", sender -> sender.hasPermission("amgui.announce"));
        ADMIN_COMMANDS.put("export", sender -> sender.hasPermission("amgui.export"));
        ADMIN_COMMANDS.put("protect", Capability.PROTECT::allows); ADMIN_COMMANDS.put("automod", Capability.AUTOMOD::allows);
        ADMIN_COMMANDS.put("reports", Capability.REPORTS::allows); ADMIN_COMMANDS.put("cases", Capability.CASES::allows);
        ADMIN_COMMANDS.put("case", Capability.CASES::allows); ADMIN_COMMANDS.put("audit", Capability.AUDIT::allows);
        ADMIN_COMMANDS.put("geoip", sender -> sender.hasPermission("amgui.geoip") && sender.hasPermission("amgui.viewip"));
        ADMIN_COMMANDS.put("backup", Capability.BACKUP_ANY::allows);
        ADMIN_COMMANDS.put("raid", Capability.ANTIRAID::allows); ADMIN_COMMANDS.put("doctor", Capability.DOCTOR::allows);
        ADMIN_COMMANDS.put("language", Capability.ADMIN::allows); ADMIN_COMMANDS.put("cleanup", Capability.ADMIN::allows);
        ADMIN_COMMANDS.put("simulate", Capability.SIMULATOR::allows);
        ADMIN_COMMANDS.put("privacy", sender -> sender.hasPermission("amgui.privacy.purge"));
        MOD_COMMANDS.put("player", Capability.PLAYER::allows); MOD_COMMANDS.put("groups", Capability.GROUPS::allows);
        MOD_COMMANDS.put("online", Capability.PLAYER::allows); MOD_COMMANDS.put("history", Capability.LOGS::allows);
        MOD_COMMANDS.put("investigate", sender -> sender.hasPermission("amgui.investigate"));
        MOD_COMMANDS.put("alert", sender -> sender.hasPermission("amgui.alert"));
        MOD_COMMANDS.put("inbox", Capability.INBOX::allows); MOD_COMMANDS.put("simulator", Capability.SIMULATOR::allows);
        MOD_COMMANDS.put("incident", Capability.INCIDENT::allows);
        MOD_COMMANDS.put("snapshot", sender -> sender.hasPermission("amgui.incident.snapshot"));
    }

    private CapabilityRegistry() { }
    public static boolean allows(CommandSender sender, Capability capability) { return capability.allows(sender); }
    public static boolean allows(CommandSender sender, String permission) { return sender.hasPermission(permission); }
    public static List<String> visibleAdminCommands(CommandSender sender) {
        List<String> result = new ArrayList<>();
        ADMIN_COMMANDS.forEach((name, allowed) -> { if (allowed.test(sender)) result.add(name); });
        return List.copyOf(result);
    }
    public static List<String> visibleBackupCommands(CommandSender sender) {
        List<String> result = new ArrayList<>();
        if (sender.hasPermission("amgui.backup.create")) result.add("create");
        if (Capability.BACKUP_ANY.allows(sender)) result.add("list");
        if (sender.hasPermission("amgui.backup.verify")) result.add("verify");
        if (sender.hasPermission("amgui.backup.restore")) result.addAll(List.of("preview", "restore", "pending", "approve", "reject", "cancel"));
        return List.copyOf(result);
    }
    public static List<String> visibleModCommands(CommandSender sender) {
        List<String> result = new ArrayList<>();
        MOD_COMMANDS.forEach((name, allowed) -> { if (allowed.test(sender)) result.add(name); });
        return List.copyOf(result);
    }
}
