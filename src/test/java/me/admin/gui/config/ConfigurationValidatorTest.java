package me.admin.gui.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationValidatorTest {

    @Test
    void acceptsSafeLocalConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gui.items-per-page", 45);
        config.set("ip-geolocation.enabled", true);
        config.set("ip-geolocation.endpoint", "https://example.test/{ip}");
        config.set("web-panel.enabled", true);
        config.set("web-panel.bind-address", "127.0.0.1");
        config.set("web-panel.token", "0123456789abcdef");

        ConfigurationValidator.Result result = ConfigurationValidator.validate(config);

        assertTrue(result.valid(), () -> String.join(", ", result.errors()));
    }

    @Test
    void rejectsUnsafeExternalHttpAndInvalidRanges() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gui.items-per-page", 99);
        config.set("ip-geolocation.enabled", true);
        config.set("ip-geolocation.endpoint", "http://example.test/{ip}");
        config.set("web-panel.enabled", true);
        config.set("web-panel.bind-address", "0.0.0.0");
        config.set("web-panel.token", "short");

        ConfigurationValidator.Result result = ConfigurationValidator.validate(config);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("gui.items-per-page")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("HTTPS")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("allow-external")));
    }

    @Test
    void acceptsLeastPrivilegeScopedWebTokenWithoutPlaintextSecret() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ip-geolocation.enabled", false);
        config.set("web-panel.enabled", true);
        config.set("web-panel.bind-address", "127.0.0.1");
        config.set("web-panel.token", "");
        config.set("web-panel.tokens.monitoring.sha256", "a".repeat(64));
        config.set("web-panel.tokens.monitoring.scopes", java.util.List.of("health", "stats"));

        ConfigurationValidator.Result result = ConfigurationValidator.validate(config);

        assertTrue(result.valid(), () -> String.join(", ", result.errors()));
    }
}
