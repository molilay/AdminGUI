package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Deterministic, side-effect-free security policy simulator. */
public final class SecuritySimulator {
    public record AutoModMatch(String ruleId, String name, String action, String reason, String violation) { }
    public record RaidActor(boolean newPlayer, boolean trusted, String address) { }
    public record RaidResult(boolean triggered, String trigger, String explanation,
                             AntiRaidSignalWindow.Snapshot snapshot) { }

    private final AdvancedModeratorGUI plugin;

    public SecuritySimulator(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    /** Reads configuration into a private snapshot and never calls AutoMod check/execute/audit APIs. */
    public List<AutoModMatch> simulateAutoMod(String text) {
        if (text == null || text.isBlank()) return List.of();
        String clean = text.substring(0, Math.min(text.length(), 4096));
        String normalized = AutoModManager.normalizeForMatching(clean);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "automod.yml"));
        ConfigurationSection rules = yaml.getConfigurationSection("rules");
        if (rules == null) return List.of();
        List<AutoModMatch> result = new ArrayList<>();
        for (String id : rules.getKeys(false)) {
            ConfigurationSection rule = rules.getConfigurationSection(id);
            if (rule == null || !rule.getBoolean("enabled", true)) continue;
            String violation = matches(id, rule, clean, normalized);
            if (violation != null) result.add(new AutoModMatch(id, rule.getString("name", id),
                    rule.getString("action", "WARN").toUpperCase(Locale.ROOT),
                    rule.getString("reason", "Violation"), violation));
        }
        return List.copyOf(result);
    }

    private static String matches(String id, ConfigurationSection rule, String clean, String normalized) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "swearing" -> rule.getStringList("triggers").stream()
                    .map(AutoModManager::normalizeForMatching).anyMatch(normalized::contains) ? "TRIGGER" : null;
            case "caps" -> matchesCaps(clean, rule.getInt("min-length", 5), rule.getInt("caps-percent", 70)) ? "CAPS" : null;
            case "flood" -> hasRepeatedCodePoint(normalized, Math.max(2, rule.getInt("char-repeat", 5))) ? "FLOOD" : null;
            case "advertising" -> safeRegexMatch(clean, rule.getStringList("patterns")) ? "ADVERTISING" : null;
            default -> rule.getStringList("triggers").stream()
                    .map(AutoModManager::normalizeForMatching).anyMatch(normalized::contains) ? "TRIGGER" : null;
        };
    }

    static boolean matchesCaps(String text, int min, int percent) {
        if (text.length() < Math.max(1, min)) return false;
        int letters = 0, upper = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (Character.isLetter(cp)) { letters++; if (Character.isUpperCase(cp)) upper++; }
        }
        return letters > 0 && upper * 100D / letters >= Math.clamp(percent, 1, 100);
    }

    static boolean hasRepeatedCodePoint(String text, int threshold) {
        int previous = -1, run = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            run = cp == previous ? run + 1 : 1; previous = cp;
            if (run >= threshold) return true;
        }
        return false;
    }

    private static boolean safeRegexMatch(String text, List<String> expressions) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        for (String expression : expressions.stream().limit(32).toList()) {
            RegexGuard.Validation validation = RegexGuard.validate(expression, 192);
            if (!validation.safe()) continue;
            try { if (Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(normalized).find()) return true; }
            catch (RuntimeException ignored) { }
        }
        return false;
    }

    public RaidResult simulateRaid(List<RaidActor> actors) {
        long now = 1_000_000_000L;
        AntiRaidSignalWindow window = new AntiRaidSignalWindow();
        AntiRaidSignalWindow.Settings settings = settings();
        AntiRaidSignalWindow.Detection last = AntiRaidSignalWindow.Detection.none(AntiRaidSignalWindow.Snapshot.empty());
        int index = 0;
        for (RaidActor actor : actors.stream().limit(500).toList()) {
            long firstPlayed = actor.newPlayer() ? 0L : now - (actor.trusted() ? settings.trustedAgeMillis() + 1 : settings.newPlayerAgeMillis() + 1);
            last = window.record(new UUID(0L, ++index), now + index, firstPlayed, actor.address(), settings);
            if (last.triggered()) break;
        }
        return new RaidResult(last.triggered(), last.trigger(), last.explanation(), last.snapshot());
    }

    public RaidResult simulateRaid(int joins, int newPlayers, int sameSubnet) {
        List<RaidActor> actors = new ArrayList<>();
        int safeJoins = Math.clamp(joins, 0, 500);
        for (int i = 0; i < safeJoins; i++) actors.add(new RaidActor(i < newPlayers, false,
                i < sameSubnet ? "198.51.100." + (i % 250 + 1) : "203.0." + (i % 250) + "." + (i % 250 + 1)));
        return simulateRaid(actors);
    }

    private AntiRaidSignalWindow.Settings settings() {
        return new AntiRaidSignalWindow.Settings(
                Math.clamp(plugin.getConfig().getLong("antiraid.window-seconds", 20), 5, 300) * 1000L,
                Math.clamp(plugin.getConfig().getInt("antiraid.joins-threshold", 15), 3, 500),
                Math.clamp(plugin.getConfig().getInt("antiraid.new-player-threshold", 10), 3, 500),
                Math.clamp(plugin.getConfig().getInt("antiraid.same-ip-threshold", 8), 3, 100),
                Math.clamp(plugin.getConfig().getLong("antiraid.new-player-age-seconds", 60), 0, 604800) * 1000L,
                Math.clamp(plugin.getConfig().getLong("antiraid.trusted-account-age-seconds", 604800), 60, 31_536_000) * 1000L,
                Math.clamp(plugin.getConfig().getDouble("antiraid.hysteresis-ratio", .6), .1, .95),
                Math.clamp(plugin.getConfig().getLong("antiraid.trigger-cooldown-seconds", 120), 0, 3600) * 1000L);
    }
}
