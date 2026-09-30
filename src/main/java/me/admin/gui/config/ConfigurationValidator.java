package me.admin.gui.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates a candidate configuration before it is applied to live managers. */
public final class ConfigurationValidator {

    public record Result(List<String> errors, List<String> warnings) {
        public Result { errors = List.copyOf(errors); warnings = List.copyOf(warnings); }
        public boolean valid() { return errors.isEmpty(); }
    }

    private ConfigurationValidator() {}

    public static Result validate(YamlConfiguration config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        range(config, "gui.items-per-page", 9, 45, errors);
        range(config, "discord.queue-size", 10, 10000, errors);
        range(config, "discord.timeout-ms", 1000, 30000, errors);
        range(config, "ip-geolocation.max-in-flight", 1, 64, errors);
        range(config, "ip-geolocation.max-cache-entries", 50, 20000, errors);
        range(config, "antiraid.window-seconds", 5, 300, errors);
        range(config, "antiraid.joins-threshold", 3, 500, errors);
        range(config, "antiraid.same-ip-threshold", 3, 100, errors);
        range(config, "punishment-security.actions-per-minute", 1, 1000, errors);
        range(config, "persistence.queue-capacity", 8, 1024, errors);
        range(config, "audit.queue-capacity", 128, 65536, errors);
        range(config, "runtime.executor.threads", 1, 8, errors);
        range(config, "runtime.executor.queue-capacity", 16, 4096, errors);
        range(config, "runtime.luckperms-timeout-ms", 250, 10000, errors);
        range(config, "runtime.player-context-refresh-ticks", 10, 1200, errors);
        range(config, "chat-history.max-per-player", 10, 2000, errors);
        range(config, "privacy.raw-ip-retention-days", 1, 365, errors);
        range(config, "privacy.max-raw-ip-history-per-player", 1, 100, errors);
        range(config, "privacy.max-ip-identities-per-player", 5, 500, errors);

        String endpoint = config.getString("ip-geolocation.endpoint", "");
        if (config.getBoolean("ip-geolocation.enabled", true) && !endpoint.startsWith("https://")) {
            errors.add("ip-geolocation.endpoint должен использовать HTTPS");
        }
        if (config.getBoolean("web-panel.enabled", false)) validateWebBinding(config, errors, warnings);
        if (config.getBoolean("antiraid.block-new-joins", false)) {
            warnings.add("antiraid.block-new-joins включён: новые входы могут быть временно заблокированы");
        }
        if (config.getBoolean("automod.enabled", true)
                && config.getBoolean("automod.approval.enabled", true)
                && config.getInt("automod.approval.expire-seconds", 900) < 30) {
            errors.add("automod.approval.expire-seconds должен быть не меньше 30");
        }
        return new Result(errors, warnings);
    }

    private static void validateWebBinding(YamlConfiguration config, List<String> errors, List<String> warnings) {
        String bind = config.getString("web-panel.bind-address", "127.0.0.1");
        String token = config.getString("web-panel.token", "");
        boolean hasLegacy = token != null && token.length() >= 16;
        boolean hasScoped = validateScopedWebTokens(config, errors, warnings);
        if (!hasLegacy && !hasScoped) {
            errors.add("web-panel требует legacy token длиной от 16 символов или хотя бы один scoped token");
        }
        if (hasLegacy) warnings.add("web-panel.token предоставляет полный legacy-доступ; рекомендуется scoped SHA-256 token");
        try {
            InetAddress address = InetAddress.getByName(bind);
            if (!address.isLoopbackAddress()) {
                if (!config.getBoolean("web-panel.allow-external", false)) {
                    errors.add("внешний web-panel требует web-panel.allow-external: true либо reverse proxy с TLS");
                } else {
                    warnings.add("web-panel доступен не только локально; используйте TLS reverse proxy и firewall");
                }
            }
        } catch (Exception e) {
            errors.add("некорректный web-panel.bind-address: " + bind);
        }
    }

    private static boolean validateScopedWebTokens(YamlConfiguration config, List<String> errors, List<String> warnings) {
        var section = config.getConfigurationSection("web-panel.tokens");
        if (section == null) return false;
        Set<String> allowed = Set.of("all", "health", "stats", "moderation", "audit");
        boolean valid = false;
        long now = System.currentTimeMillis();
        for (String id : section.getKeys(false)) {
            String hash = section.getString(id + ".sha256", "").trim().toLowerCase(java.util.Locale.ROOT);
            List<String> scopes = section.getStringList(id + ".scopes").stream()
                    .map(value -> value.toLowerCase(java.util.Locale.ROOT).trim()).toList();
            if (!hash.matches("[0-9a-f]{64}")) {
                errors.add("web-panel.tokens." + id + ".sha256 должен содержать 64 hex-символа");
                continue;
            }
            if (scopes.isEmpty() || scopes.stream().anyMatch(value -> !allowed.contains(value))) {
                errors.add("web-panel.tokens." + id + ".scopes содержит неизвестный или пустой scope");
                continue;
            }
            long expiresAt = section.getLong(id + ".expires-at", 0L);
            if (expiresAt < 0L) errors.add("web-panel.tokens." + id + ".expires-at не может быть отрицательным");
            else if (expiresAt > 0L && expiresAt <= now) warnings.add("web-panel token '" + id + "' уже истёк");
            valid = true;
        }
        return valid;
    }

    private static void range(YamlConfiguration config, String path, int min, int max, List<String> errors) {
        if (!config.contains(path)) return;
        int value = config.getInt(path, Integer.MIN_VALUE);
        if (value < min || value > max) errors.add(path + " должен быть в диапазоне " + min + ".." + max);
    }
}
