package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.utils.BanService;
import me.admin.gui.utils.SensitiveCommandFilter;
import me.admin.gui.utils.TextUtil;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AutoModManager {
    private final AdvancedModeratorGUI plugin;
    private final File configFile;
    private final File feedbackFile;
    private final AutoModApprovalQueue approvalQueue;
    private YamlConfiguration config;

    private volatile Map<String, Rule> rules = Map.of();
    private final Map<UUID, PlayerChatData> playerData = new ConcurrentHashMap<>();
    private final Map<String, Long> ruleCooldowns = new ConcurrentHashMap<>();
    private final EscalationTracker escalationHistory = new EscalationTracker();

    private volatile boolean checkChat = true;
    private volatile boolean checkCommands;
    private volatile boolean checkSigns;
    private volatile String bypassPermission = "amgui.automod.bypass";
    private volatile boolean notifyStaff = true;
    private volatile boolean blacklistEnabled;
    private volatile List<String> blacklistWords = List.of();
    private volatile List<Pattern> blacklistPatterns = List.of();
    private volatile boolean monitorMode;
    private volatile boolean normalizeText = true;
    private volatile boolean foldHomoglyphs = true;
    private volatile List<String> allowedDomains = List.of();
    private volatile List<String> allowedWords = List.of();
    private volatile Set<String> ignoredWorlds = Set.of();
    private volatile Set<String> ignoredCommands = Set.of();
    private volatile int maxMessageLength = 512;
    private volatile boolean escalationEnabled;
    private volatile int escalationWindowSeconds = 3600;
    private volatile List<EscalationStep> escalationSteps = List.of();
    private volatile int maxRules = 128;
    private volatile int maxPatternsPerRule = 16;
    private volatile int maxPatternLength = 192;
    private volatile long regexBudgetNanos = 2_500_000L;
    private volatile long maxAutomaticMuteSeconds = 3600L;

    public enum Action { WARN, MUTE, KICK, BAN, TEMPBAN }
    private record EscalationStep(int threshold, Action action, long duration) {}

    public static final class Rule {
        String id;
        volatile boolean enabled;
        String name;
        Action action;
        volatile long duration;
        String reason;
        int warns;
        String message;
        double fine;
        volatile String group;
        String category;
        int severity;
        volatile String autoDisabledReason = "";
        List<String> triggers;
        List<Pattern> compiledPatterns;
        int minLength;
        int capsPercent;
        int messages;
        int seconds;
        int charRepeat;
        int repeatCount;
        int cooldownSeconds;
        Set<String> groups;

        public String getId() { return id; }
        public boolean isEnabled() { return enabled; }
        public String getName() { return name; }
        public Action getAction() { return action; }
        public long getDuration() { return duration; }
        public String getReason() { return reason; }
        public List<String> getTriggers() { return triggers; }
        public String getGroup() { return group; }
        public String getCategory() { return category; }
        public int getSeverity() { return severity; }
        public String getAutoDisabledReason() { return autoDisabledReason; }
    }

    private static final class PlayerChatData {
        final List<Long> timestamps = new ArrayList<>();
        final List<String> lastMessages = new ArrayList<>();
    }

    public record CheckResult(Rule rule, ViolationType violation, String message) {}
    public record ApprovalResult(boolean success, String message, AutoModApprovalQueue.Request request) {}

    public enum ViolationType {
        TRIGGER("триггер"), CAPS("капс"), SPAM("спам"), ADVERTISING("реклама"),
        FLOOD("флуд"), REPEATED("повтор");
        private final String display;
        ViolationType(String display) { this.display = display; }
        public String getDisplayName() { return display; }
    }

    public AutoModManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "automod.yml");
        this.feedbackFile = new File(plugin.getDataFolder(), "automod-feedback.yml");
        this.approvalQueue = new AutoModApprovalQueue(new File(plugin.getDataFolder(), "automod-pending.yml"));
        load();
    }

    public void load() {
        playerData.clear();
        ruleCooldowns.clear();
        escalationHistory.clearAll();
        resetSettingsToDefaults();
        if (!configFile.exists()) plugin.saveResource("automod.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
        blacklistEnabled = plugin.getConfig().getBoolean("blacklist.enabled", true);
        blacklistWords = List.copyOf(plugin.getConfig().getStringList("blacklist.words"));

        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            checkChat = settings.getBoolean("check-chat", true);
            checkCommands = settings.getBoolean("check-commands", false);
            checkSigns = settings.getBoolean("check-signs", false);
            bypassPermission = settings.getString("bypass-permission", "amgui.automod.bypass");
            notifyStaff = settings.getBoolean("notify-staff", true);
            monitorMode = settings.getBoolean("monitor-mode", false);
            normalizeText = settings.getBoolean("normalize-text", true);
            foldHomoglyphs = settings.getBoolean("fold-homoglyphs", true);
            maxMessageLength = Math.clamp(settings.getInt("max-message-length", 512), 64, 4096);
            escalationEnabled = settings.getBoolean("escalation.enabled", false);
            escalationWindowSeconds = Math.clamp(settings.getInt("escalation.window-seconds", 3600), 60, 86400);
            escalationSteps = parseEscalation(settings.getStringList("escalation.steps"));
            maxRules = Math.clamp(settings.getInt("limits.max-rules", 128), 1, 512);
            maxPatternsPerRule = Math.clamp(settings.getInt("regex.max-patterns-per-rule", 16), 1, 64);
            maxPatternLength = Math.clamp(settings.getInt("regex.max-pattern-length", 192), 32, 512);
            regexBudgetNanos = Math.clamp(settings.getLong("regex.budget-micros", 2500L), 100L, 50_000L) * 1000L;
            maxAutomaticMuteSeconds = Math.clamp(settings.getLong("safety.max-automatic-mute-seconds", 3600L), 60L, 86400L);
            allowedDomains = settings.getStringList("allowed-domains").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()).toList();
            allowedWords = settings.getStringList("allowed-words").stream()
                    .map(AutoModManager::normalizeForMatching).filter(value -> !value.isBlank()).toList();
            ignoredWorlds = settings.getStringList("ignored-worlds").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            ignoredCommands = settings.getStringList("ignored-commands").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                    .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
        }
        blacklistPatterns = blacklistWords.stream()
                .map(word -> normalizeText ? normalizeForMatching(word) : word.toLowerCase(Locale.ROOT))
                .filter(word -> !word.isBlank())
                .map(word -> Pattern.compile("(?:^|\\P{L})" + Pattern.quote(word) + "(?:$|\\P{L})"))
                .toList();

        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            rules = Map.of();
            return;
        }
        Map<String, Rule> loaded = new LinkedHashMap<>();
        for (String id : rulesSection.getKeys(false)) {
            if (loaded.size() >= maxRules) {
                plugin.getLogger().warning("AutoMod: rule limit reached (" + maxRules + "); remaining rules ignored.");
                break;
            }
            try {
                Rule rule = loadRule(id, rulesSection.getConfigurationSection(id));
                if (rule != null) loaded.put(id, rule);
            } catch (Exception exception) {
                plugin.getLogger().warning("AutoMod: cannot load rule '" + id + "': " + exception.getMessage());
            }
        }
        rules = Collections.unmodifiableMap(loaded);
        plugin.getLogger().info("AutoMod: loaded " + loaded.size() + " rules.");
    }

    private void resetSettingsToDefaults() {
        checkChat = true;
        checkCommands = false;
        checkSigns = false;
        bypassPermission = "amgui.automod.bypass";
        notifyStaff = true;
        monitorMode = false;
        normalizeText = true;
        foldHomoglyphs = true;
        allowedDomains = List.of();
        allowedWords = List.of();
        ignoredWorlds = Set.of();
        ignoredCommands = Set.of();
        maxMessageLength = 512;
        escalationEnabled = false;
        escalationWindowSeconds = 3600;
        escalationSteps = List.of();
        maxRules = 128;
        maxPatternsPerRule = 16;
        maxPatternLength = 192;
        regexBudgetNanos = 2_500_000L;
        maxAutomaticMuteSeconds = 3600L;
    }

    public void reload() { load(); }

    private Rule loadRule(String id, ConfigurationSection section) {
        if (section == null) return null;
        Rule rule = new Rule();
        rule.id = id;
        rule.enabled = section.getBoolean("enabled", true);
        rule.name = section.getString("name", id);
        try {
            rule.action = Action.valueOf(section.getString("action", "WARN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            rule.action = Action.WARN;
            plugin.getLogger().warning("AutoMod: unknown action in '" + id + "'; WARN selected.");
        }
        rule.duration = Math.max(0L, section.getLong("duration", 0L));
        rule.reason = section.getString("reason", "Нарушение");
        rule.warns = Math.max(1, section.getInt("warns", 1));
        rule.message = section.getString("message", "&cНарушение!").replace('&', '§');
        rule.fine = Math.max(0D, section.getDouble("fine", 0D));
        rule.group = section.getString("group", "");
        rule.category = section.getString("category", id).trim().toLowerCase(Locale.ROOT);
        if (rule.category.isEmpty()) rule.category = id;
        rule.severity = Math.clamp(section.getInt("severity", defaultSeverity(rule.action)), 1, 5);
        rule.triggers = new CopyOnWriteArrayList<>(section.getStringList("triggers"));

        List<Pattern> patterns = new ArrayList<>();
        List<String> rawPatterns = section.getStringList("patterns");
        if (rawPatterns.size() > maxPatternsPerRule) {
            plugin.getLogger().warning("AutoMod: rule '" + id + "' pattern list truncated to " + maxPatternsPerRule + ".");
        }
        for (String expression : rawPatterns.stream().limit(maxPatternsPerRule).toList()) {
            try {
                RegexGuard.Validation validation = RegexGuard.validate(expression, maxPatternLength);
                if (!validation.safe()) {
                    plugin.getLogger().warning("AutoMod: unsafe regex skipped in '" + id + "' (" + validation.reason() + ").");
                    continue;
                }
                patterns.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (Exception exception) {
                plugin.getLogger().warning("AutoMod: invalid regex in '" + id + "': " + exception.getMessage());
            }
        }
        rule.compiledPatterns = List.copyOf(patterns);
        rule.minLength = Math.max(1, section.getInt("min-length", 5));
        rule.capsPercent = Math.clamp(section.getInt("caps-percent", 70), 1, 100);
        rule.messages = Math.max(2, section.getInt("messages", 4));
        rule.seconds = Math.max(1, section.getInt("seconds", 5));
        rule.charRepeat = Math.max(2, section.getInt("char-repeat", 5));
        rule.repeatCount = Math.max(2, section.getInt("repeat-count", 3));
        rule.cooldownSeconds = Math.clamp(section.getInt("cooldown-seconds", 5), 0, 3600);
        rule.groups = section.getStringList("groups").stream().map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return rule;
    }

    public boolean isBypassed(Player player) { return player.hasPermission(bypassPermission); }
    public boolean shouldIgnore(Player player) {
        return isBypassed(player) || ignoredWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }
    public boolean shouldIgnore(PlayerModerationContext context) {
        return context.autoModBypass() || ignoredWorlds.contains(context.world());
    }
    public String getBypassPermission() { return bypassPermission; }
    public boolean isCheckChat() { return checkChat; }
    public boolean isCheckCommands() { return checkCommands; }
    public boolean isCheckSigns() { return checkSigns; }
    public boolean isNotifyStaff() { return notifyStaff; }
    public boolean isMonitorMode() { return monitorMode; }
    public boolean isBlacklistEnabled() { return blacklistEnabled; }
    public Map<String, Rule> getRules() { return rules; }
    public YamlConfiguration getConfig() { return config; }

    public boolean isBlacklisted(String message) {
        String normalized = normalizeText ? normalizeForMatching(message) : message.toLowerCase(Locale.ROOT);
        for (Pattern pattern : blacklistPatterns) if (pattern.matcher(normalized).find()) return true;
        return false;
    }

    public void executeBlacklist(Player player) {
        String requested = plugin.getConfigManager().getBlacklistAction().toLowerCase(Locale.ROOT);
        String reason = plugin.getConfigManager().getBlacklistReason();
        if (monitorMode) {
            notifyDetection(player, "Blacklist: " + reason);
            plugin.getAuditManager().record(null, "AutoMod", "automod.blacklist-detect", player.getName(),
                    player.getUniqueId(), "monitor=true; reason=" + reason);
            return;
        }
        if ("ban".equals(requested) || "tempban".equals(requested)) {
            enqueueApproval(player, "tempban".equals(requested) ? Action.TEMPBAN : Action.BAN,
                    reason, 0L, "blacklist");
            applySafetyMute(player, reason, safetyFallbackDuration());
        } else if ("warn".equals(requested)) {
            plugin.getWarnManager().warn(player, reason, "Blacklist");
        } else {
            applySafetyMute(player, reason, safetyFallbackDuration());
        }
        plugin.getAuditManager().record(null, "AutoMod", "automod.blacklist-execute", player.getName(),
                player.getUniqueId(), "requested=" + requested + "; reason=" + reason);
    }

    public void setCheckChat(boolean value) { checkChat = value; setAndSave("settings.check-chat", value); }
    public void setCheckCommands(boolean value) { checkCommands = value; setAndSave("settings.check-commands", value); }
    public void setCheckSigns(boolean value) { checkSigns = value; setAndSave("settings.check-signs", value); }
    public void setNotifyStaff(boolean value) { notifyStaff = value; setAndSave("settings.notify-staff", value); }
    public void setMonitorMode(boolean value) { monitorMode = value; setAndSave("settings.monitor-mode", value); }

    private void setAndSave(String path, Object value) { config.set(path, value); saveConfig(); }
    public void toggleRule(String id) {
        Rule rule = rules.get(id);
        if (rule == null) return;
        rule.enabled = !rule.enabled;
        rule.autoDisabledReason = "";
        setAndSave("rules." + id + ".enabled", rule.enabled);
    }
    public void setRuleDuration(String id, long duration) {
        Rule rule = rules.get(id);
        if (rule == null) return;
        rule.duration = Math.max(0L, duration);
        setAndSave("rules." + id + ".duration", rule.duration);
    }
    public void addRuleTrigger(String id, String word) {
        Rule rule = rules.get(id);
        if (rule == null || word == null || word.isBlank() || rule.triggers.size() >= 512) return;
        rule.triggers.add(word);
        setAndSave("rules." + id + ".triggers", rule.triggers);
    }
    public void removeRuleTrigger(String id, String word) {
        Rule rule = rules.get(id);
        if (rule == null) return;
        rule.triggers.remove(word);
        setAndSave("rules." + id + ".triggers", rule.triggers);
    }
    public void setRuleGroup(String id, String group) {
        Rule rule = rules.get(id);
        if (rule == null) return;
        rule.group = group;
        setAndSave("rules." + id + ".group", group.isEmpty() ? null : group);
    }
    public void saveConfig() {
        YamlPersistenceService.queueYaml(plugin, configFile, config, "automod config");
    }

    public void clearPlayerData(UUID uuid) {
        playerData.remove(uuid);
        ruleCooldowns.keySet().removeIf(key -> key.startsWith(uuid + ":"));
        escalationHistory.clear(uuid);
    }

    public CheckResult check(PlayerModerationContext context, String message, boolean command) {
        if (message == null || message.isEmpty() || shouldIgnore(context)) return null;
        if (command) {
            String name = SensitiveCommandFilter.commandName(message);
            if (SensitiveCommandFilter.isSensitive(message) || ignoredCommands.contains(name)) return null;
        }
        String clean = message.replace("§", "");
        if (clean.length() > maxMessageLength) clean = clean.substring(0, maxMessageLength);
        String normalized = normalizeText ? normalizeForMatching(clean) : clean.toLowerCase(Locale.ROOT);
        if (!foldHomoglyphs && normalizeText) normalized = normalizeWithoutHomoglyphs(clean);
        for (String allowed : allowedWords) normalized = normalized.replace(allowed, "");

        long now = System.currentTimeMillis();
        for (Rule rule : rules.values()) {
            if (!rule.enabled) continue;
            String cooldownKey = context.uuid() + ":" + rule.id;
            Long last = ruleCooldowns.get(cooldownKey);
            if (last != null && now - last < rule.cooldownSeconds * 1000L) continue;
            if (!rule.groups.isEmpty()) {
                if (!rule.groups.contains(context.primaryGroup())) continue;
            }
            ViolationType violation = checkRule(rule, clean, normalized, context.uuid());
            if (violation != null) {
                if (rule.cooldownSeconds > 0) ruleCooldowns.put(cooldownKey, now);
                return new CheckResult(rule, violation, clean);
            }
        }
        return null;
    }

    private ViolationType checkRule(Rule rule, String message, String normalized, UUID playerId) {
        return switch (rule.id) {
            case "swearing" -> checkSwearing(rule, normalized);
            case "caps" -> checkCaps(rule, message);
            case "spam" -> checkSpam(rule, playerId);
            case "advertising" -> checkAdvertising(rule, message);
            case "flood" -> checkFlood(rule, normalized);
            case "repeated-message" -> checkRepeatedMessage(rule, normalized, playerId);
            default -> null;
        };
    }

    private ViolationType checkSwearing(Rule rule, String message) {
        String compactMessage = compact(message);
        for (String trigger : rule.triggers) {
            if (compactMessage.contains(compact(normalizeForMatching(trigger)))) return ViolationType.TRIGGER;
        }
        return null;
    }

    private ViolationType checkCaps(Rule rule, String message) {
        if (message.length() < rule.minLength) return null;
        int letters = 0;
        int upper = 0;
        for (int offset = 0; offset < message.length();) {
            int codePoint = message.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                letters++;
                if (Character.isUpperCase(codePoint)) upper++;
            }
        }
        return letters > 0 && upper * 100D / letters >= rule.capsPercent ? ViolationType.CAPS : null;
    }

    private ViolationType checkSpam(Rule rule, UUID playerId) {
        PlayerChatData data = playerData.computeIfAbsent(playerId, ignored -> new PlayerChatData());
        long now = System.currentTimeMillis();
        synchronized (data.timestamps) {
            data.timestamps.add(now);
            data.timestamps.removeIf(timestamp -> now - timestamp > rule.seconds * 1000L);
            return data.timestamps.size() >= rule.messages ? ViolationType.SPAM : null;
        }
    }

    private ViolationType checkAdvertising(Rule rule, String message) {
        String filtered = Normalizer.normalize(message, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        for (String domain : allowedDomains) filtered = filtered.replace(domain, "");
        long started = System.nanoTime();
        for (Pattern pattern : rule.compiledPatterns) {
            boolean match = pattern.matcher(filtered).find();
            long elapsed = System.nanoTime() - started;
            if (elapsed > regexBudgetNanos) {
                quarantineSlowRule(rule, elapsed);
                return null;
            }
            if (match) return ViolationType.ADVERTISING;
        }
        return null;
    }

    private void quarantineSlowRule(Rule rule, long elapsedNanos) {
        synchronized (rule) {
            if (!rule.autoDisabledReason.isEmpty()) return;
            rule.enabled = false;
            rule.autoDisabledReason = "regex-budget:" + elapsedNanos / 1000L + "us";
        }
        plugin.getTaskExecutor().runOnMain(() -> {
            String details = "rule=" + rule.id + "; elapsedMicros=" + elapsedNanos / 1000L;
            plugin.getLogger().warning("AutoMod disabled slow rule '" + rule.id + "' for this runtime: " + details);
            plugin.getAuditManager().record(null, "AutoMod", "automod.rule-circuit-breaker", rule.id, null, details);
            Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission("amgui.automod.manage"))
                    .forEach(staff -> staff.sendMessage("§8[§cAutoMod§8] §cПравило §f" + rule.name
                            + " §cотключено: превышен regex-бюджет."));
        });
    }

    private ViolationType checkFlood(Rule rule, String message) {
        if (message.length() < rule.charRepeat) return null;
        int last = -1;
        int repeated = 0;
        for (int offset = 0; offset < message.length();) {
            int codePoint = message.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) continue;
            repeated = codePoint == last ? repeated + 1 : 1;
            last = codePoint;
            if (repeated >= rule.charRepeat) return ViolationType.FLOOD;
        }
        return null;
    }

    private ViolationType checkRepeatedMessage(Rule rule, String message, UUID playerId) {
        PlayerChatData data = playerData.computeIfAbsent(playerId, ignored -> new PlayerChatData());
        synchronized (data.lastMessages) {
            data.lastMessages.add(message);
            if (data.lastMessages.size() > rule.repeatCount) data.lastMessages.remove(0);
            if (data.lastMessages.size() < rule.repeatCount) return null;
            String first = data.lastMessages.getFirst();
            return data.lastMessages.stream().allMatch(first::equals) ? ViolationType.REPEATED : null;
        }
    }

    public void execute(Player player, CheckResult result, boolean command) {
        Rule rule = result.rule();
        if (monitorMode) {
            notifyDetection(player, rule.reason);
            plugin.getAuditManager().record(null, "AutoMod", "automod.detect", player.getName(),
                    player.getUniqueId(), "rule=" + rule.id + "; monitor=true; command=" + command);
            return;
        }
        player.sendMessage(rule.message);
        Action requested = rule.action;
        long requestedDuration = rule.duration;
        EscalationStep escalation = registerViolation(player.getUniqueId(), rule);
        if (escalation != null) {
            requested = escalation.action();
            requestedDuration = escalation.duration();
        }

        boolean pendingApproval = requested == Action.BAN || requested == Action.TEMPBAN;
        if (pendingApproval) enqueueApproval(player, requested, rule.reason, requestedDuration, rule.id);
        Action applied = requested;
        long appliedDuration = requestedDuration;
        if (requested == Action.KICK || pendingApproval) {
            applied = Action.MUTE;
            appliedDuration = safetyFallbackDuration();
        } else if (requested == Action.MUTE) {
            appliedDuration = safeMuteDuration(requestedDuration);
        }

        switch (applied) {
            case WARN -> {
                plugin.getWarnManager().warn(player, rule.reason, "AutoMod");
                for (int i = 1; i < rule.warns; i++) plugin.getWarnManager().warn(player, rule.reason, "AutoMod");
                if (rule.fine > 0D) plugin.getVaultIntegration().ifPresent(vault -> vault.fine(player, rule.fine));
            }
            case MUTE -> {
                applySafetyMute(player, rule.reason, appliedDuration);
                if (rule.fine > 0D) plugin.getVaultIntegration().ifPresent(vault -> vault.fine(player, rule.fine));
            }
            case KICK, BAN, TEMPBAN -> throw new IllegalStateException("unsafe automatic action was not downgraded");
        }

        if (rule.group != null && !rule.group.isEmpty()) {
            try {
                var group = plugin.getLuckPermsIntegration().getGroup(rule.group);
                if (group != null) plugin.getLuckPermsIntegration().setGroup(player, group);
            } catch (Exception exception) {
                plugin.getLogger().warning("AutoMod: cannot assign quarantine group '" + rule.group + "': " + exception.getMessage());
            }
        }

        Action finalApplied = applied;
        long finalDuration = appliedDuration;
        plugin.getDiscordWebhook().ifPresent(webhook -> webhook.send("automod", player.getName(), "AutoMod", rule.reason,
                finalDuration > 0L ? TimeUtils.formatDuration(finalDuration) : "—"));
        plugin.getAuditManager().record(null, "AutoMod", "automod.execute", player.getName(), player.getUniqueId(),
                "rule=" + rule.id + "; category=" + rule.category + "; severity=" + rule.severity
                        + "; requested=" + requested + "; applied=" + finalApplied + "; duration=" + finalDuration
                        + "; pending=" + pendingApproval + "; escalated=" + (escalation != null) + "; command=" + command);
        notifyDetection(player, rule.reason + " [" + rule.category + "/" + rule.severity + "]");
    }

    private void applySafetyMute(Player player, String reason, long duration) {
        plugin.getMuteManager().mute(player, reason, "AutoMod", safeMuteDuration(duration));
    }

    private long safeMuteDuration(long requested) {
        return requested <= 0L ? maxAutomaticMuteSeconds : Math.min(requested, maxAutomaticMuteSeconds);
    }

    private long safetyFallbackDuration() {
        return safeMuteDuration(config.getLong("settings.safety.pending-mute-seconds", 300L));
    }

    private void notifyDetection(Player target, String reason) {
        if (!notifyStaff) return;
        String message = "§8[§cAutoMod§8] §f" + target.getName() + " §7→ §c" + reason;
        Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission("amgui.staffchat") && !staff.equals(target))
                .forEach(staff -> staff.sendMessage(message));
    }

    private AutoModApprovalQueue.Request enqueueApproval(Player player, Action action, String reason,
                                                         long duration, String ruleId) {
        expirePendingApprovals();
        int maxPending = Math.clamp(config.getInt("settings.approval.max-pending", 500), 10, 5000);
        if (approvalQueue.list().size() >= maxPending) {
            plugin.getAuditManager().record(null, "AutoMod", "automod.approval-queue-full", player.getName(),
                    player.getUniqueId(), "action=" + action + "; max=" + maxPending);
            Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission("amgui.automod.approve"))
                    .forEach(staff -> staff.sendMessage("§8[§cAutoMod§8] §cОчередь одобрений заполнена; бан не создан."));
            return null;
        }
        long safeDuration = action == Action.TEMPBAN && duration <= 0L
                ? Math.clamp(config.getLong("settings.approval.default-tempban-seconds", 86400L), 300L, 31_536_000L)
                : Math.max(0L, duration);
        AutoModApprovalQueue.Request request;
        try {
            request = approvalQueue.enqueue(player.getUniqueId(), player.getName(), action,
                    reason, safeDuration, ruleId);
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning("AutoMod approval request was not persisted; destructive action cancelled: "
                    + exception.getMessage());
            plugin.getAuditManager().record(null, "AutoMod", "automod.approval-save-failed", player.getName(),
                    player.getUniqueId(), "action=" + action + "; error=" + exception.getClass().getSimpleName());
            return null;
        }
        String message = "§8[§6AutoMod approval§8] §e#" + request.id() + " §f" + action + " §7для §f"
                + player.getName() + "§7. §e/amgui automod approve " + request.id();
        Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission("amgui.automod.approve"))
                .forEach(staff -> staff.sendMessage(message));
        plugin.getAuditManager().record(null, "AutoMod", "automod.approval-request", player.getName(),
                player.getUniqueId(), "id=" + request.id() + "; action=" + action + "; rule=" + ruleId);
        return request;
    }

    public List<AutoModApprovalQueue.Request> getPendingApprovals() {
        expirePendingApprovals();
        return approvalQueue.list();
    }

    public synchronized ApprovalResult approvePending(int id, CommandSender approver) {
        AutoModApprovalQueue.Request request = approvalQueue.get(id);
        if (request == null) return new ApprovalResult(false, "Запрос не найден или уже истёк.", null);
        if (request.action() != Action.BAN && request.action() != Action.TEMPBAN) {
            return new ApprovalResult(false, "Запрос содержит неподдерживаемое действие.", request);
        }
        if (!approver.hasPermission("amgui.automod.approve")) {
            return new ApprovalResult(false, "Недостаточно права amgui.automod.approve.", request);
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(request.targetId());
        PunishmentSecurityManager.Decision decision = plugin.getPunishmentSecurityManager()
                .validate(approver, target, request.action() == Action.TEMPBAN ? "tempban" : "ban");
        if (!decision.allowed()) return new ApprovalResult(false, decision.reason(), request);
        try {
            // Persist the dequeue before the destructive action to guarantee at-most-once approval.
            if (approvalQueue.remove(id) == null) {
                return new ApprovalResult(false, "Запрос уже обработан другим сотрудником.", null);
            }
            Instant expiration = request.action() == Action.TEMPBAN && request.duration() > 0L
                    ? Instant.ofEpochMilli(System.currentTimeMillis() + request.duration() * 1000L) : null;
            BanService.banProfile(target, request.reason(), expiration, approver.getName());
        } catch (Exception exception) {
            if (approvalQueue.get(id) == null) {
                try { approvalQueue.restore(request); }
                catch (Exception restoreFailure) {
                    plugin.getLogger().severe("Cannot restore failed approval #" + id + ": " + restoreFailure.getMessage());
                }
            }
            plugin.getAuditManager().record(null, approver.getName(), "automod.approval-failed", request.targetName(),
                    request.targetId(), "id=" + id + "; error=" + exception.getClass().getSimpleName());
            return new ApprovalResult(false, "Не удалось выполнить запрос: " + exception.getMessage(), request);
        }
        Player online = target.getPlayer();
        if (online != null) {
            try { online.kick(TextUtil.legacy("§cВы заблокированы.\n§7Причина: " + request.reason())); }
            catch (Exception exception) { plugin.getLogger().warning("Approved ban applied, but kick failed: " + exception.getMessage()); }
        }
        plugin.getAuditManager().record(null, approver.getName(), "automod.approval-approved", request.targetName(),
                request.targetId(), "id=" + id + "; action=" + request.action() + "; duration=" + request.duration());
        return new ApprovalResult(true, "Запрос #" + id + " подтверждён.", request);
    }

    public synchronized ApprovalResult rejectPending(int id, CommandSender approver) {
        AutoModApprovalQueue.Request request = approvalQueue.get(id);
        if (request == null) return new ApprovalResult(false, "Запрос не найден или уже истёк.", null);
        if (!approver.hasPermission("amgui.automod.approve")) {
            return new ApprovalResult(false, "Недостаточно права amgui.automod.approve.", request);
        }
        try { approvalQueue.remove(id); }
        catch (IllegalStateException exception) {
            return new ApprovalResult(false, "Не удалось надёжно сохранить отклонение: " + exception.getMessage(), request);
        }
        plugin.getAuditManager().record(null, approver.getName(), "automod.approval-rejected", request.targetName(),
                request.targetId(), "id=" + id + "; action=" + request.action());
        return new ApprovalResult(true, "Запрос #" + id + " отклонён.", request);
    }

    public int expirePendingApprovals() {
        long seconds = Math.clamp(config == null ? 86400L : config.getLong("settings.approval.expire-seconds", 86400L),
                300L, 604800L);
        List<AutoModApprovalQueue.Request> expired;
        try { expired = approvalQueue.expireBefore(System.currentTimeMillis() - seconds * 1000L); }
        catch (IllegalStateException exception) {
            plugin.getLogger().warning("AutoMod approval expiration failed: " + exception.getMessage());
            return 0;
        }
        for (AutoModApprovalQueue.Request request : expired) {
            plugin.getAuditManager().record(null, "AutoMod", "automod.approval-expired", request.targetName(),
                    request.targetId(), "id=" + request.id() + "; action=" + request.action());
        }
        return expired.size();
    }

    public List<String> testMessage(String message) {
        if (message == null || message.isBlank()) return List.of();
        String clean = message.length() > maxMessageLength ? message.substring(0, maxMessageLength) : message;
        String normalized = normalizeText ? normalizeForMatching(clean) : clean.toLowerCase(Locale.ROOT);
        if (!foldHomoglyphs && normalizeText) normalized = normalizeWithoutHomoglyphs(clean);
        for (String allowed : allowedWords) normalized = normalized.replace(allowed, "");
        List<String> matches = new ArrayList<>();
        for (Rule rule : rules.values()) {
            if (!rule.enabled) continue;
            ViolationType violation = switch (rule.id) {
                case "swearing" -> checkSwearing(rule, normalized);
                case "caps" -> checkCaps(rule, clean);
                case "advertising" -> checkAdvertising(rule, clean);
                case "flood" -> checkFlood(rule, normalized);
                default -> null;
            };
            if (violation != null) matches.add(rule.id + ":" + violation.name());
        }
        return List.copyOf(matches);
    }

    public synchronized void addAllowedWord(String word) {
        String clean = normalizeForMatching(word);
        if (clean.isBlank() || allowedWords.contains(clean)) return;
        List<String> changed = new ArrayList<>(allowedWords);
        changed.add(clean);
        allowedWords = List.copyOf(changed);
        setAndSave("settings.allowed-words", changed);
    }

    public synchronized void recordFalsePositive(String actor, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isBlank()) return;
        YamlConfiguration feedback = YamlConfiguration.loadConfiguration(feedbackFile);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<?, ?> source : feedback.getMapList("false-positives")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(String.valueOf(key), value));
            rows.add(copy);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", System.currentTimeMillis());
        row.put("actor", actor);
        row.put("text", clean);
        row.put("matched", testMessage(clean));
        rows.addFirst(row);
        int keep = Math.clamp(config.getInt("settings.feedback.keep-last", 200), 10, 2000);
        if (rows.size() > keep) rows.subList(keep, rows.size()).clear();
        feedback.set("false-positives", rows);
        YamlPersistenceService.queueYaml(plugin, feedbackFile, feedback, "automod feedback");
        addAllowedWord(clean);
    }

    private EscalationStep registerViolation(UUID uuid, Rule rule) {
        if (!escalationEnabled || escalationSteps.isEmpty()) return null;
        int count = escalationHistory.record(uuid, rule.category, rule.severity, System.currentTimeMillis(),
                escalationWindowSeconds * 1000L);
        EscalationStep selected = null;
        for (EscalationStep step : escalationSteps) if (count >= step.threshold()) selected = step;
        return selected;
    }

    private List<EscalationStep> parseEscalation(List<String> values) {
        List<EscalationStep> result = new ArrayList<>();
        for (String value : values) {
            try {
                String[] parts = value.split(":", -1);
                if (parts.length != 3) throw new IllegalArgumentException("threshold:ACTION:duration expected");
                result.add(new EscalationStep(Math.clamp(Integer.parseInt(parts[0]), 2, 100),
                        Action.valueOf(parts[1].toUpperCase(Locale.ROOT)),
                        Math.clamp(Long.parseLong(parts[2]), 0L, 31_536_000L)));
            } catch (Exception exception) {
                plugin.getLogger().warning("AutoMod: escalation step skipped '" + value + "': " + exception.getMessage());
            }
        }
        result.sort(Comparator.comparingInt(EscalationStep::threshold));
        return List.copyOf(result);
    }

    private static int defaultSeverity(Action action) {
        return switch (action) {
            case WARN -> 1;
            case MUTE -> 2;
            case KICK -> 3;
            case TEMPBAN -> 4;
            case BAN -> 5;
        };
    }

    public static String normalizeForMatching(String input) {
        if (input == null) return "";
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.getType(codePoint) == Character.FORMAT) continue;
            int replacement = switch (codePoint) {
                case '0' -> 'o'; case '1' -> 'i'; case '3' -> 'з'; case '4' -> 'ч';
                case '6' -> 'б'; case '7' -> 't'; case '8' -> 'b'; case '@' -> 'a';
                case 'а' -> 'a'; case 'е', 'ё' -> 'e'; case 'о' -> 'o'; case 'р' -> 'p';
                case 'с' -> 'c'; case 'у' -> 'y'; case 'х' -> 'x'; case 'к' -> 'k';
                case 'м' -> 'm'; case 'т' -> 't'; case 'в' -> 'b'; case 'н' -> 'h';
                default -> codePoint;
            };
            result.appendCodePoint(replacement);
        }
        return result.toString().replaceAll("[\\p{Z}\\p{Punct}_]+", " ").trim();
    }

    private static String normalizeWithoutHomoglyphs(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{Cf}", "").replaceAll("[\\p{Z}\\p{Punct}_]+", " ").trim();
    }

    private static String compact(String input) { return input.replaceAll("[^\\p{L}\\p{N}]", ""); }
}
