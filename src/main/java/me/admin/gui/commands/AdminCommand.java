package me.admin.gui.commands;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.database.LogEntry;
import me.admin.gui.manager.ProtectedPlayersManager;
import me.admin.gui.manager.ModerationCaseManager;
import me.admin.gui.utils.CapabilityRegistry;
import me.admin.gui.utils.CapabilityRegistry.Capability;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AdvancedModeratorGUI plugin;

    public AdminCommand(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§8[§cAM§8] §7AdvancedModeratorGUI v" + plugin.getPluginMeta().getVersion());
            List<String> visible = CapabilityRegistry.visibleAdminCommands(sender);
            sender.sendMessage(visible.isEmpty() ? plugin.getConfigManager().getMessage("no-permission")
                    : plugin.getLocalizationManager().format("messages.command-help", "&7Доступно: &f/amgui %commands%",
                    "commands", String.join(" &8| &f", visible)));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("amgui.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                AdvancedModeratorGUI.ReloadResult result = plugin.reloadPluginConfiguration();
                if (!result.success()) {
                    sender.sendMessage("§cКонфигурация не применена:");
                    result.errors().forEach(error -> sender.sendMessage(" §c- §f" + error));
                    plugin.getAuditManager().record(sender, "config.reload-rejected", "AdvancedModeratorGUI", null,
                            String.join("; ", result.errors()));
                    return true;
                }
                plugin.getHeadCacheManager().clearCache();
                plugin.getAuditManager().record(sender, "config.reload", "AdvancedModeratorGUI", null,
                        "warnings=" + result.warnings().size());
                sender.sendMessage("§a✓ Конфигурация проверена и перезагружена.");
                result.warnings().forEach(warning -> sender.sendMessage(" §e⚠ §f" + warning));
            }
            case "check" -> {
                if (!sender.hasPermission("amgui.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                sender.sendMessage("§8[§cAM§8] §7Статус:");
                sender.sendMessage(" §7- Окружение: " + (me.admin.gui.compat.ServerCompat.isSupported()
                        ? "§a" : "§e") + me.admin.gui.compat.ServerCompat.describe());
                sender.sendMessage(" §7- LuckPerms: " + (plugin.getLuckPermsIntegration() != null ? "§a✓" : "§c✗"));
                sender.sendMessage(" §7- База данных: " + (plugin.getDatabaseManager().isConnected() ? "§a✓" : "§c✗"));
                sender.sendMessage(" §7- Discord Webhook: " + (plugin.getDiscordWebhook().map(d -> d.isEnabled() ? "§a✓" : "§7✗").orElse("§c✗")));
                sender.sendMessage(" §7- Vault: " + (plugin.getVaultIntegration().map(v -> v.isEnabled() ? "§a✓" : "§7✗").orElse("§c✗")));
                sender.sendMessage(" §7- AuthMe: " + (plugin.getAuthMeIntegration().map(a -> a.isEnabled() ? "§a✓" : "§7✗").orElse("§c✗")));
                sender.sendMessage(" §7- ViaVersion: " + (plugin.getViaVersionIntegration().map(v -> v.isEnabled() ? "§a✓" : "§7✗").orElse("§c✗")));
                sender.sendMessage(" §7- Аудит: " + (plugin.getAuditManager().isIntegrityValid() ? "§a✓ цел" : "§c✗ повреждён"));
                sender.sendMessage(" §7- Открытые дела: §f" + plugin.getModerationCaseManager().getOpen().size());
                sender.sendMessage(" §7- AutoMod: " + (plugin.getAutoModManager().isMonitorMode() ? "§eMONITOR" : "§aACTIVE"));
                sender.sendMessage(" §7- Web API: " + (plugin.getWebPanel().isRunning() ? "§a✓ " + plugin.getWebPanel().getPublicAddress() : "§7✗"));
                sender.sendMessage(" §7- GeoIP: " + (plugin.getGeoIpManager().isEnabled() ? "§a✓" : "§7✗"));
                sender.sendMessage(" §7- YAML-бэкапы: §f" + plugin.getYamlBackupManager().listBackups().size());
            }
            case "announce" -> {
                if (!sender.hasPermission("amgui.announce")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /amgui announce <сообщение>");
                    return true;
                }
                String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                String formatted = "§8[§cℹ§8] §f" + msg;
                Bukkit.getServer().broadcast(me.admin.gui.utils.TextUtil.legacy(formatted));
                plugin.getAuditManager().record(sender, "announcement.send", "server", null, msg);
                plugin.getDiscordWebhook().ifPresent(w -> w.send("announce", "Сервер", "AMGUI", msg, "—"));
                sender.sendMessage("§a✓ Сообщение отправлено всем игрокам.");
            }
            case "export" -> {
                if (!sender.hasPermission("amgui.export")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /amgui export <ник>");
                    return true;
                }
                String targetName = args[1];
                int queryLimit = Math.max(100, plugin.getConfig().getInt("database.gui-query-limit", 5000));
                List<LogEntry> logs = plugin.getDatabaseManager().getLogs(queryLimit, 0, targetName);
                if (logs.isEmpty()) {
                    sender.sendMessage("§eНет логов для §f" + targetName);
                    return true;
                }
                try {
                    File exportDir = new File(plugin.getDataFolder(), "exports");
                    if (!exportDir.exists() && !exportDir.mkdirs()) {
                        throw new java.io.IOException("не удалось создать каталог exports");
                    }
                    String safeTargetName = targetName.replaceAll("[^A-Za-z0-9_-]", "_");
                    File file = new File(exportDir, safeTargetName + "_" + System.currentTimeMillis() + ".json");
                    try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
                        fw.write("[\n");
                        for (int i = 0; i < logs.size(); i++) {
                            LogEntry e = logs.get(i);
                            fw.write(String.format(
                                    "  {\"id\":%d,\"type\":\"%s\",\"moderator\":\"%s\",\"target\":\"%s\",\"reason\":\"%s\",\"date\":\"%s\"}",
                                    e.getId(), escapeJson(e.getType()), escapeJson(e.getModerator()), escapeJson(e.getTarget()),
                                    escapeJson(e.getReason()), e.getDate().toString()));
                            if (i < logs.size() - 1) fw.write(",\n");
                            else fw.write("\n");
                        }
                        fw.write("]\n");
                    }
                    sender.sendMessage("§a✓ Экспортировано §f" + logs.size() + " §aзаписей в §f" + file.getName());
                    plugin.getAuditManager().record(sender, "logs.export", targetName, null,
                            "file=" + file.getName() + "; entries=" + logs.size());
                } catch (Exception e) {
                    sender.sendMessage("§cОшибка экспорта: " + e.getMessage());
                }
            }
            case "reports" -> {
                if (!sender.hasPermission("amgui.report.staff")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cТолько для игроков.");
                    return true;
                }
                new me.admin.gui.gui.ReportsGUI(plugin, player).open();
            }
            case "automod" -> {
                if (!sender.hasPermission("amgui.automod.view")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("test")) {
                    String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    List<String> matches = plugin.getAutoModManager().testMessage(message);
                    sender.sendMessage(matches.isEmpty() ? "§a✓ Правила не сработали." : "§cСработали: §f" + String.join(", ", matches));
                } else if (args.length >= 3 && args[1].equalsIgnoreCase("allow")) {
                    if (!sender.hasPermission("amgui.automod.edit")) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                        return true;
                    }
                    String word = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                    plugin.getAutoModManager().recordFalsePositive(sender.getName(), word);
                    plugin.getAuditManager().record(sender, "automod.false-positive", word, null, "added-to-allowed-words");
                    sender.sendMessage("§a✓ Исключение добавлено в allowed-words: §f" + word);
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("pending")) {
                    List<me.admin.gui.manager.AutoModApprovalQueue.Request> pending = plugin.getAutoModManager().getPendingApprovals();
                    sender.sendMessage("§8[§cAutoMod§8] §7Ожидают решения: §f" + pending.size());
                    pending.stream().limit(30).forEach(request -> sender.sendMessage(" §8- §e#" + request.id()
                            + " §f" + request.targetName() + " §8→ §c" + request.action()
                            + " §7правило=§f" + request.ruleId()));
                } else if (args.length >= 3 && (args[1].equalsIgnoreCase("approve") || args[1].equalsIgnoreCase("reject"))) {
                    if (!sender.hasPermission("amgui.automod.approve")) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                        return true;
                    }
                    int id;
                    try { id = Integer.parseInt(args[2]); }
                    catch (NumberFormatException error) { sender.sendMessage("§cID должен быть числом."); return true; }
                    var decision = args[1].equalsIgnoreCase("approve")
                            ? plugin.getAutoModManager().approvePending(id, sender)
                            : plugin.getAutoModManager().rejectPending(id, sender);
                    sender.sendMessage((decision.success() ? "§a✓ " : "§c") + decision.message());
                } else {
                    if (!sender.hasPermission("amgui.automod.edit")) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                        return true;
                    }
                    plugin.getAutoModManager().reload();
                    sender.sendMessage("§a✓ AutoMod перезагружен (§f" + plugin.getAutoModManager().getRules().size() + " правил§a).");
                }
            }
            case "cases" -> {
                if (!sender.hasPermission("amgui.cases") || !(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage(sender instanceof Player ? "no-permission" : "only-players"));
                    return true;
                }
                new me.admin.gui.gui.CasesGUI(plugin, player).open();
            }
            case "case" -> handleCase(sender, args);
            case "geoip" -> handleGeoIp(sender, args);
            case "backup" -> handleBackup(sender, args);
            case "raid" -> handleRaid(sender, args);
            case "doctor" -> handleDoctor(sender);
            case "simulate" -> handleSimulation(sender, args);
            case "privacy" -> handlePrivacy(sender, args);
            case "audit" -> {
                if (!sender.hasPermission("amgui.audit")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                sender.sendMessage("§7Целостность audit.log: " + (plugin.getAuditManager().isIntegrityValid() ? "§a✓" : "§c✗"));
                if (sender instanceof Player player) new me.admin.gui.gui.AuditGUI(plugin, player).open();
            }
            case "language" -> {
                if (!sender.hasPermission("amgui.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§7Текущий язык: §f" + plugin.getLocalizationManager().getLanguage());
                    return true;
                }
                String language = args[1].toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                plugin.getConfig().set("language", language);
                plugin.saveConfig();
                plugin.getLocalizationManager().reload();
                plugin.getAuditManager().record(sender, "language.change", language, null, "");
                sender.sendMessage("§a✓ Язык: §f" + plugin.getLocalizationManager().getLanguage());
            }
            case "cleanup" -> {
                if (!sender.hasPermission("amgui.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                int days;
                try {
                    days = args.length >= 2 ? Math.max(1, Integer.parseInt(args[1]))
                            : plugin.getConfig().getInt("database.retention-days", 365);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cКоличество дней должно быть числом.");
                    return true;
                }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    int removed = plugin.getDatabaseManager().purgeOlderThan(days);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getAuditManager().record(sender, "logs.cleanup", "database", null,
                                "days=" + days + "; removed=" + removed);
                        sender.sendMessage("§a✓ Удалено старых записей: §f" + removed);
                    });
                });
            }
            case "protect" -> {
                if (!sender.hasPermission("amgui.protect")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                ProtectedPlayersManager ppm = plugin.getProtectedPlayersManager();
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование:");
                    sender.sendMessage(" §7/amgui protect add <ник> §8- §fдобавить защиту");
                    sender.sendMessage(" §7/amgui protect remove <ник> §8- §fснять защиту");
                    sender.sendMessage(" §7/amgui protect list §8- §fсписок защищённых");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "add" -> {
                        if (args.length < 3) {
                            sender.sendMessage("§cУкажите ник игрока.");
                            return true;
                        }
                        if (ppm.addPlayer(args[2])) {
                            sender.sendMessage("§a✓ Игрок §f" + args[2] + " §aтеперь защищён от любых наказаний.");
                            plugin.getLogger().warning("Protected player ADDED: " + args[2] + " by " + sender.getName());
                            plugin.getAuditManager().record(sender, "protection.add", args[2], null, "");
                        } else {
                            sender.sendMessage("§cИгрок не найден или уже в списке.");
                        }
                    }
                    case "remove" -> {
                        if (args.length < 3) {
                            sender.sendMessage("§cУкажите ник игрока.");
                            return true;
                        }
                        if (ppm.removePlayer(args[2])) {
                            sender.sendMessage("§c✓ Защита снята с §f" + args[2]);
                            plugin.getLogger().warning("Protected player REMOVED: " + args[2] + " by " + sender.getName());
                            plugin.getAuditManager().record(sender, "protection.remove", args[2], null, "");
                        } else {
                            sender.sendMessage("§cИгрок не найден в списке защищённых.");
                        }
                    }
                    case "list" -> {
                        List<String> list = ppm.getProtectedNames();
                        if (list.isEmpty()) {
                            sender.sendMessage("§eНет защищённых игроков.");
                        } else {
                            sender.sendMessage("§8[§cAM§8] §7Защищённые игроки (§f" + list.size() + "§7):");
                            for (String n : list) {
                                sender.sendMessage(" §8- §f" + n);
                            }
                        }
                    }
                    default -> sender.sendMessage("§cИспользуйте: add, remove, list");
                }
            }
            default -> {
                sender.sendMessage("§cНеизвестная команда. Используйте /amgui reload | cases | case | audit | automod | geoip | backup | language");
            }
        }

        return true;
    }

    private void handleSimulation(CommandSender sender, String[] args) {
        if (!sender.hasPermission("amgui.simulator")) { sender.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
        if (args.length >= 3 && args[1].equalsIgnoreCase("automod")) {
            String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            var matches = plugin.getSecuritySimulator().simulateAutoMod(text);
            sender.sendMessage(matches.isEmpty() ? "§a✓ Совпадений нет. DRY-RUN." : "§cСовпадений: §f" + matches.size() + " §8(DRY-RUN)");
            matches.stream().limit(30).forEach(match -> sender.sendMessage(" §8- §e" + match.ruleId() + " §7/ " + match.violation() + " → §c" + match.action()));
            return;
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("raid")) {
            try {
                int joins = Integer.parseInt(args[2]), fresh = Integer.parseInt(args[3]), subnet = Integer.parseInt(args[4]);
                if (joins < 0 || fresh < 0 || subnet < 0 || joins > 500 || fresh > joins || subnet > joins) throw new IllegalArgumentException();
                var result = plugin.getSecuritySimulator().simulateRaid(joins, fresh, subnet);
                sender.sendMessage(result.triggered() ? "§cLockdown сработал бы: §f" + result.trigger() + " §8(DRY-RUN)"
                        : "§aLockdown не сработал бы. §8(DRY-RUN)");
                sender.sendMessage("§7unique=" + result.snapshot().uniqueJoins() + ", new=" + result.snapshot().newPlayers()
                        + ", subnet=" + result.snapshot().largestSubnet());
            } catch (RuntimeException error) { sender.sendMessage("§cИспользование: /amgui simulate raid <входы> <новые> <одна-подсеть>"); }
            return;
        }
        if (sender instanceof Player player && args.length == 1) { new me.admin.gui.gui.SecuritySimulatorGUI(plugin, player).open(); return; }
        sender.sendMessage("§e/amgui simulate automod <текст>");
        sender.sendMessage("§e/amgui simulate raid <входы> <новые> <одна-подсеть>");
    }

    private void handlePrivacy(CommandSender sender, String[] args) {
        if (!sender.hasPermission("amgui.privacy.purge")) { sender.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
        if (args.length != 3 || !args[1].equalsIgnoreCase("purge")) { sender.sendMessage("§cИспользование: /amgui privacy purge <игрок>"); return; }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null) { sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found")); return; }
        Runnable purge = () -> {
            if (!sender.hasPermission("amgui.privacy.purge")) { sender.sendMessage(plugin.getConfigManager().getMessage("no-permission")); return; }
            plugin.getAltDetector().purgePlayer(target.getUniqueId());
            plugin.getChatHistoryManager().clearHistory(target.getUniqueId());
            plugin.getAuditManager().record(sender, "privacy.purge", target.getName(), target.getUniqueId(),
                    "scope=alt-ip-index,chat-memory; raw-ip=false");
            sender.sendMessage("§a✓ Приватные IP-связи и история чата в памяти очищены для §f" + target.getName() + "§a.");
        };
        if (sender instanceof Player player) new me.admin.gui.gui.ConfirmGUI(plugin, player,
                "§cОчистить приватные данные " + target.getName() + "?", purge).open();
        else purge.run();
    }

    private void handleCase(CommandSender sender, String[] args) {
        if (!sender.hasPermission("amgui.cases")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/amgui case create <игрок> <название>");
            sender.sendMessage("§e/amgui case assign|status|priority|deadline|note|attach-report|attach-evidence <id> <значение>");
            sender.sendMessage("§e/amgui case template <игрок> <шаблон> | export <id>");
            return;
        }
        String casePermission = switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "export" -> "amgui.cases.export";
            case "assign" -> "amgui.cases.assign";
            default -> "amgui.cases.edit";
        };
        if (!sender.hasPermission(casePermission)
                || (casePermission.equals("amgui.cases.export") && !sender.hasPermission("amgui.viewip"))) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        try {
            switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                case "create" -> {
                    if (args.length < 4) throw new IllegalArgumentException("Использование: /amgui case create <игрок> <название>");
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
                    if (target == null) throw new IllegalArgumentException("Игрок не найден в кэше сервера.");
                    String title = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    int id = plugin.getModerationCaseManager().create(target.getUniqueId(), target.getName(), title, sender.getName());
                    sender.sendMessage(id > 0 ? "§a✓ Создано дело #" + id : "§cДостигнут лимит открытых дел для игрока.");
                }
                case "assign" -> {
                    requireArgs(args, 4, "/amgui case assign <id> <модератор>");
                    boolean changed = plugin.getModerationCaseManager().assign(Integer.parseInt(args[2]), args[3], sender.getName());
                    sender.sendMessage(changed ? "§a✓ Ответственный назначен." : "§cДело не найдено.");
                }
                case "status" -> {
                    requireArgs(args, 4, "/amgui case status <id> <open|investigating|resolved|rejected> [причина]");
                    ModerationCaseManager.Status status = ModerationCaseManager.Status.valueOf(args[3].toUpperCase(java.util.Locale.ROOT));
                    String reason = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "Команда";
                    boolean changed = plugin.getModerationCaseManager().setStatus(Integer.parseInt(args[2]), status, sender.getName(), reason);
                    sender.sendMessage(changed ? "§a✓ Статус изменён." : "§cДело не найдено.");
                }
                case "note" -> {
                    requireArgs(args, 4, "/amgui case note <id> <текст>");
                    boolean changed = plugin.getModerationCaseManager().addNote(Integer.parseInt(args[2]), sender.getName(),
                            String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                    sender.sendMessage(changed ? "§a✓ Заметка добавлена." : "§cДело не найдено.");
                }
                case "priority" -> {
                    requireArgs(args, 4, "/amgui case priority <id> <low|normal|high|critical>");
                    ModerationCaseManager.Priority priority = ModerationCaseManager.Priority.valueOf(args[3].toUpperCase(java.util.Locale.ROOT));
                    boolean changed = plugin.getModerationCaseManager().setPriority(Integer.parseInt(args[2]), priority, sender.getName());
                    sender.sendMessage(changed ? "§a✓ Приоритет изменён." : "§cДело не найдено.");
                }
                case "deadline" -> {
                    requireArgs(args, 4, "/amgui case deadline <id> <6ч|1д|7д>");
                    long seconds = me.admin.gui.utils.TimeUtils.parseDuration(args[3]);
                    if (seconds <= 0) throw new IllegalArgumentException("Неверная длительность дедлайна.");
                    boolean changed = plugin.getModerationCaseManager().setDeadline(Integer.parseInt(args[2]),
                            System.currentTimeMillis() + seconds * 1000L, sender.getName());
                    sender.sendMessage(changed ? "§a✓ Дедлайн изменён." : "§cДело не найдено.");
                }
                case "template" -> {
                    requireArgs(args, 4, "/amgui case template <игрок> <шаблон>");
                    org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
                    if (target == null) throw new IllegalArgumentException("Игрок не найден в кэше сервера.");
                    int id = plugin.getModerationCaseManager().createFromTemplate(target.getUniqueId(),
                            target.getName() == null ? args[2] : target.getName(), args[3], sender.getName());
                    sender.sendMessage(id > 0 ? "§a✓ Создано дело #" + id + " по шаблону." : "§cШаблон не найден или достигнут лимит дел.");
                }
                case "export" -> {
                    requireArgs(args, 3, "/amgui case export <id>");
                    int id = Integer.parseInt(args[2]);
                    sender.sendMessage("§eФормирование экспорта дела #" + id + "...");
                    plugin.getCaseExportManager().exportAsync(id).whenComplete((result, failure) ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (failure != null) {
                                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                                    sender.sendMessage("§cОшибка экспорта: " + cause.getMessage());
                                } else {
                                    sender.sendMessage("§a✓ " + result.file().getFileName() + " §7SHA-256: §f" + result.sha256());
                                    plugin.getAuditManager().record(sender, "case.export", "case-" + id, null,
                                            "file=" + result.file().getFileName() + "; sha256=" + result.sha256() + "; database=false");
                                }
                            }));
                }
                case "attach-report" -> {
                    requireArgs(args, 4, "/amgui case attach-report <id> <report-id>");
                    boolean changed = plugin.getModerationCaseManager().attachReport(Integer.parseInt(args[2]), Integer.parseInt(args[3]), sender.getName());
                    sender.sendMessage(changed ? "§a✓ Жалоба прикреплена." : "§cДело не найдено.");
                }
                case "attach-evidence" -> {
                    requireArgs(args, 4, "/amgui case attach-evidence <id> <evidence-id>");
                    boolean changed = plugin.getModerationCaseManager().attachEvidence(Integer.parseInt(args[2]), Integer.parseInt(args[3]), sender.getName());
                    sender.sendMessage(changed ? "§a✓ Улика прикреплена." : "§cДело не найдено.");
                }
                default -> throw new IllegalArgumentException("Неизвестная операция с делом.");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
        }
    }

    private void handleGeoIp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("amgui.geoip") || !sender.hasPermission("amgui.viewip")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: /amgui geoip <игрок|IP>");
            return;
        }
        String query = args[1].trim();
        OfflinePlayer target = null;
        String ip = query;
        Player online = Bukkit.getPlayerExact(query);
        if (online != null) {
            target = online;
            ip = online.getAddress() == null ? "" : online.getAddress().getAddress().getHostAddress();
        } else if (!query.contains(".") && !query.contains(":")) {
            target = Bukkit.getOfflinePlayerIfCached(query);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                return;
            }
            ip = plugin.getAltDetector().getLastIp(target.getUniqueId());
        }
        if (ip == null || ip.isBlank()) {
            sender.sendMessage("§eIP игрока ещё не записан.");
            return;
        }
        final OfflinePlayer resolvedTarget = target;
        final String resolvedIp = ip;
        if (sender instanceof Player player) {
            new me.admin.gui.gui.GeoIpGUI(plugin, player, resolvedIp, resolvedTarget).open();
            return;
        }
        sender.sendMessage("§eПолучение приблизительной GeoIP-информации...");
        plugin.getGeoIpManager().lookup(resolvedIp).whenComplete((result, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        sender.sendMessage("§cGeoIP: " + cause.getMessage());
                        return;
                    }
                    sender.sendMessage("§8[§cAM§8] §f" + result.ip() + " §7→ §f" + result.locationLine());
                    sender.sendMessage("§7Провайдер: §f" + (result.isp().isBlank() ? "—" : result.isp())
                            + " §8| §7Timezone: §f" + (result.timezone().isBlank() ? "—" : result.timezone()));
                    plugin.getAuditManager().record(sender, "ip.geolocation", resolvedTarget == null ? resolvedIp : resolvedTarget.getName(),
                            resolvedTarget == null ? null : resolvedTarget.getUniqueId(),
                            "provider=ipwho.is; network=" + me.admin.gui.utils.IpPrivacyUtil.mask(result.ip()));
                }));
    }

    private void handleBackup(CommandSender sender, String[] args) {
        if (!CapabilityRegistry.allows(sender, Capability.BACKUP_ANY)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            List<java.nio.file.Path> backups = plugin.getYamlBackupManager().listBackups();
            sender.sendMessage("§8[§cAM§8] §7YAML-бэкапы: §f" + backups.size());
            backups.stream().limit(10).forEach(path -> {
                try { sender.sendMessage(" §8- §f" + path.getFileName() + " §7(" + Files.size(path) / 1024 + " KiB)"); }
                catch (java.io.IOException ignored) { sender.sendMessage(" §8- §f" + path.getFileName()); }
            });
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("verify")) {
            if (!sender.hasPermission("amgui.backup.verify") || args.length < 3) {
                sender.sendMessage(args.length < 3 ? "§eИспользование: /amgui backup verify <имя.zip>"
                        : plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            String fileName = exactFileName(args[2]);
            if (fileName.isBlank()) { sender.sendMessage("§cУкажите только имя файла из /amgui backup list."); return; }
            sender.sendMessage("§eПроверка SHA-256 манифеста архива...");
            java.util.concurrent.CompletableFuture.supplyAsync(() -> plugin.getYamlBackupManager().verifyBackup(fileName))
                    .whenComplete((result, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (failure != null) sender.sendMessage("§cОшибка проверки: " + failure.getMessage());
                        else sender.sendMessage(result.valid() ? "§a✓ Архив цел: §f" + result.entries() + " §aфайлов"
                                : "§c✗ Архив повреждён: §f" + String.join("; ", result.errors()));
                        plugin.getAuditManager().record(sender, "backup.verify", fileName, null,
                                "valid=" + (failure == null && result.valid()));
                    }));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("preview")) {
            if (!sender.hasPermission("amgui.backup.restore") || args.length < 3) {
                sender.sendMessage(args.length < 3 ? "§eИспользование: /amgui backup preview <имя.zip>"
                        : plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            String fileName = exactFileName(args[2]);
            if (fileName.isBlank()) { sender.sendMessage("§cУкажите только имя файла из /amgui backup list."); return; }
            java.util.concurrent.CompletableFuture.supplyAsync(() -> plugin.getYamlBackupManager().previewRestore(fileName))
                    .whenComplete((result, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (failure != null || !result.valid()) {
                            sender.sendMessage("§cPreview не пройден: " + (failure == null ? String.join("; ", result.errors()) : failure.getMessage()));
                            return;
                        }
                        sender.sendMessage("§8[§cAM§8] §7Будет создано: §f" + result.creates()
                                + " §8| §7заменено: §f" + result.replaces());
                        result.files().stream().limit(15).forEach(file -> sender.sendMessage(" §8- §f" + file));
                    }));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            if (!sender.hasPermission("amgui.backup.restore")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            try {
                boolean cancelled = plugin.getYamlBackupManager().cancelPendingRestore();
                sender.sendMessage(cancelled ? "§a✓ Ожидающее восстановление отменено." : "§eОжидающего восстановления нет.");
                if (cancelled) plugin.getAuditManager().record(sender, "backup.restore-cancel", "pending-restore", null, "database=false");
            } catch (java.io.IOException error) {
                sender.sendMessage("§cНе удалось отменить восстановление: " + error.getMessage());
            }
            return;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("approve") || args[1].equalsIgnoreCase("reject"))) {
            handleBackupApproval(sender, args);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("pending")) {
            if (!sender.hasPermission("amgui.backup.restore")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            List<me.admin.gui.manager.SensitiveActionApprovalManager.Request> pending = plugin.getSensitiveActionApprovalManager().list();
            sender.sendMessage("§8[§cAM§8] §7Ожидают одобрения: §f" + pending.size());
            pending.forEach(request -> sender.sendMessage(" §8- §e#" + request.id() + " §f" + request.type()
                    + " §7от §f" + request.requesterName() + " §8→ §f" + request.payload()));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("restore")) {
            if (!sender.hasPermission("amgui.backup.restore")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            if (args.length < 3) {
                sender.sendMessage("§eИспользование: /amgui backup restore <имя.zip>");
                return;
            }
            String fileName = java.nio.file.Path.of(args[2]).getFileName().toString();
            if (!fileName.equals(args[2])) {
                sender.sendMessage("§cУкажите только имя файла из /amgui backup list.");
                return;
            }
            if (sender instanceof Player player) {
                new me.admin.gui.gui.ConfirmGUI(plugin, player, "§cПодготовить восстановление " + fileName + "?", () ->
                        requestOrStageBackupRestore(player, fileName)).open();
            } else if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
                stageBackupRestore(sender, fileName);
            } else {
                sender.sendMessage("§cКонсоль: повторите с суффиксом confirm. Изменения применятся при следующем запуске.");
            }
            return;
        }
        if (!sender.hasPermission("amgui.backup.create")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        sender.sendMessage("§eСоздание резервной копии YAML и audit.log...");
        plugin.getYamlBackupManager().createAsync().whenComplete((result, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        sender.sendMessage("§cОшибка бэкапа: " + cause.getMessage());
                        return;
                    }
                    sender.sendMessage("§a✓ Создан §f" + result.file().getFileName() + " §7(" + result.entries() + " файлов, "
                            + result.bytes() / 1024 + " KiB)");
                    plugin.getAuditManager().record(sender, "backup.create", result.file().getFileName().toString(), null,
                            "entries=" + result.entries() + "; bytes=" + result.bytes() + "; database=false");
                }));
    }

    private String exactFileName(String input) {
        try {
            String fileName = java.nio.file.Path.of(input).getFileName().toString();
            return fileName.equals(input) ? fileName : "";
        } catch (java.nio.file.InvalidPathException error) {
            return "";
        }
    }

    private void requestOrStageBackupRestore(Player requester, String fileName) {
        if (!plugin.getConfig().getBoolean("security.dual-approval.backup-restore", true)) {
            stageBackupRestore(requester, fileName);
            return;
        }
        var request = plugin.getSensitiveActionApprovalManager().submit(requester, "backup.restore", fileName);
        requester.sendMessage("§eСоздан запрос двух сотрудников §f#" + request.id() + "§e. Его должен одобрить другой сотрудник:");
        requester.sendMessage("§f/amgui backup approve " + request.id());
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.getUniqueId().equals(requester.getUniqueId()) && staff.hasPermission("amgui.backup.restore")) {
                staff.sendMessage("§8[§cAM§8] §e" + requester.getName() + " запрашивает restore §f" + fileName
                        + "§e. Одобрение: §f/amgui backup approve " + request.id());
            }
        }
    }

    private void handleBackupApproval(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("amgui.backup.restore")) {
            sender.sendMessage(plugin.getConfigManager().getMessage(sender instanceof Player ? "no-permission" : "only-players"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§eИспользование: /amgui backup <approve|reject> <id>");
            return;
        }
        int id;
        try { id = Integer.parseInt(args[2]); }
        catch (NumberFormatException error) { sender.sendMessage("§cID должен быть числом."); return; }
        if (args[1].equalsIgnoreCase("reject")) {
            sender.sendMessage(plugin.getSensitiveActionApprovalManager().reject(player, id)
                    ? "§a✓ Запрос отклонён." : "§cЗапрос не найден или истёк.");
            return;
        }
        var decision = plugin.getSensitiveActionApprovalManager().approve(player, id);
        if (!decision.allowed()) {
            sender.sendMessage("§c" + decision.reason());
            return;
        }
        if (!"backup.restore".equals(decision.request().type())) {
            sender.sendMessage("§cТип запроса не поддерживается этой командой.");
            return;
        }
        sender.sendMessage("§a✓ Запрос одобрен. Запускается проверка и staging...");
        stageBackupRestore(sender, decision.request().payload());
    }

    private void stageBackupRestore(CommandSender sender, String fileName) {
        sender.sendMessage("§eПроверка архива и создание аварийного бэкапа...");
        plugin.getYamlBackupManager().stageRestoreAsync(fileName).whenComplete((result, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        sender.sendMessage("§cНе удалось подготовить восстановление: " + cause.getMessage());
                        return;
                    }
                    sender.sendMessage("§a✓ Проверено YAML-файлов: §f" + result.yamlFiles());
                    sender.sendMessage("§a✓ Аварийная копия: §f" + result.emergencyBackup().getFileName());
                    sender.sendMessage("§eВосстановление применится при следующем полном запуске сервера.");
                    plugin.getAuditManager().record(sender, "backup.restore-stage", fileName, null,
                            "yaml=" + result.yamlFiles() + "; emergency=" + result.emergencyBackup().getFileName() + "; database=false");
                }));
    }

    private void handleRaid(CommandSender sender, String[] args) {
        if (!sender.hasPermission("amgui.antiraid")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage("§8[§cAntiRaid§8] §7Состояние: " + (plugin.getAntiRaidManager().isLockdown()
                    ? "§cLOCKDOWN, осталось " + plugin.getAntiRaidManager().remainingSeconds() + "с" : "§aнорма"));
            sender.sendMessage("§7Входов в текущем окне: §f" + plugin.getAntiRaidManager().recentJoinCount());
            if (sender instanceof Player player) new me.admin.gui.gui.AntiRaidGUI(plugin, player).open();
            return;
        }
        if (!sender.hasPermission("amgui.antiraid.toggle")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cВключение и выключение через команду доступно игроку; консоль может смотреть status.");
            return;
        }
        if (args[1].equalsIgnoreCase("on")) {
            long seconds = 180;
            if (args.length >= 3) {
                try { seconds = Long.parseLong(args[2]); } catch (NumberFormatException e) { sender.sendMessage("§cСекунды должны быть числом."); return; }
            }
            plugin.getAntiRaidManager().enableManual(player, seconds);
        } else if (args[1].equalsIgnoreCase("off")) {
            plugin.getAntiRaidManager().disable(player);
        } else sender.sendMessage("§eИспользование: /amgui raid <status|on [секунды]|off>");
    }

    private void handleDoctor(CommandSender sender) {
        if (!sender.hasPermission("amgui.doctor")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        me.admin.gui.manager.DoctorManager.Report report = plugin.getDoctorManager().run();
        sender.sendMessage("§8[§cAMGUI Doctor§8] §7Проверок: §f" + report.checks().size()
                + " §8| §eпредупреждений: " + report.warnings() + " §8| §cошибок: " + report.errors());
        for (me.admin.gui.manager.DoctorManager.Check check : report.checks()) {
            String color = switch (check.severity()) { case OK -> "§a"; case WARN -> "§e"; case ERROR -> "§c"; };
            sender.sendMessage(" " + color + "● §f" + check.title() + " §8— §7" + check.details());
        }
        plugin.getAuditManager().record(sender, "doctor.run", "AdvancedModeratorGUI", null,
                "warnings=" + report.warnings() + "; errors=" + report.errors() + "; database=not-checked");
        if (sender instanceof Player player) new me.admin.gui.gui.DoctorGUI(plugin, player).open();
    }

    private static void requireArgs(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException("Использование: " + usage);
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CapabilityRegistry.visibleAdminCommands(sender).stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (!CapabilityRegistry.visibleAdminCommands(sender).contains(args[0].toLowerCase(java.util.Locale.ROOT))) return List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("case")) {
            List<String> actions = new ArrayList<>();
            if (sender.hasPermission("amgui.cases.edit")) actions.addAll(List.of("create", "template", "status", "priority", "deadline", "note", "attach-report", "attach-evidence"));
            if (sender.hasPermission("amgui.cases.assign")) actions.add("assign");
            if (sender.hasPermission("amgui.cases.export") && sender.hasPermission("amgui.viewip")) actions.add("export");
            return actions.stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("case") && args[1].equalsIgnoreCase("priority")) {
            return List.of("low", "normal", "high", "critical").stream()
                    .filter(value -> value.startsWith(args[3].toLowerCase())).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("case") && args[1].equalsIgnoreCase("status")) {
            return List.of("open", "investigating", "resolved", "rejected").stream()
                    .filter(value -> value.startsWith(args[3].toLowerCase())).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("case") && args[1].equalsIgnoreCase("template")) {
            org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("moderation-cases.templates");
            if (section == null) return List.of();
            return section.getKeys(false).stream().filter(value -> value.startsWith(args[3].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("automod")) {
            List<String> actions = new ArrayList<>(List.of("test", "pending"));
            if (sender.hasPermission("amgui.automod.edit")) actions.add("allow");
            if (sender.hasPermission("amgui.automod.approve")) actions.addAll(List.of("approve", "reject"));
            return actions.stream().filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("simulate") && sender.hasPermission("amgui.simulator")) return List.of("automod", "raid");
        if (args.length == 2 && args[0].equalsIgnoreCase("privacy") && sender.hasPermission("amgui.privacy.purge")) return List.of("purge");
        if (args.length == 3 && args[0].equalsIgnoreCase("privacy") && args[1].equalsIgnoreCase("purge") && sender.hasPermission("amgui.privacy.purge")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("backup")) return CapabilityRegistry.visibleBackupCommands(sender).stream()
                .filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("backup") && args[1].equalsIgnoreCase("restore")) {
            return plugin.getYamlBackupManager().listBackups().stream().map(path -> path.getFileName().toString())
                    .filter(value -> value.startsWith(args[2])).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("raid")) return (sender.hasPermission("amgui.antiraid.toggle")
                ? List.of("status", "on", "off") : List.of("status")).stream()
                .filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("geoip")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) return List.of("ru", "en");
        if (args.length == 2 && args[0].equalsIgnoreCase("export")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("protect")) {
            return List.of("add", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("protect") &&
                (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        return List.of();
    }
}
