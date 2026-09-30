package me.admin.gui.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigurationTest {

    @Test
    void pluginMetadataAndDefaultsAreConsistent() {
        YamlConfiguration plugin = load("plugin.yml");
        YamlConfiguration config = load("config.yml");

        assertEquals("2.7.0", plugin.getString("version"));
        assertEquals("1.21", plugin.getString("api-version"));
        assertFalse(plugin.getBoolean("folia-supported"));
        assertEquals("ru", config.getString("language"));
        assertFalse(config.getBoolean("web-panel.enabled"));
        assertEquals("127.0.0.1", config.getString("web-panel.bind-address"));
        assertTrue(config.getBoolean("punishment-security.fail-closed-on-lookup-error"));
        assertFalse(config.getBoolean("punishment-security.punish-protection-violations"));
        assertTrue(config.getBoolean("backups.enabled"));
        assertTrue(config.getBoolean("ip-geolocation.enabled"));
        assertTrue(config.getString("ip-geolocation.endpoint", "").startsWith("https://"));
        assertTrue(plugin.contains("permissions.amgui.geoip"));
        assertTrue(plugin.contains("permissions.amgui.antiraid"));
        assertTrue(plugin.contains("permissions.amgui.doctor"));
        assertTrue(config.getBoolean("antiraid.enabled"));
        assertFalse(config.getBoolean("antiraid.block-new-joins"));
        assertEquals(72, config.getLong("moderation-cases.deadline-hours.normal"));
        assertTrue(config.contains("moderation-cases.templates.cheats"));
        assertEquals("lockdown", config.getString("antiraid.mode"));
        assertTrue(config.getBoolean("security.dual-approval.backup-restore"));
        assertEquals(128, config.getInt("persistence.queue-capacity"));
        assertEquals(2048, config.getInt("audit.queue-capacity"));
        assertFalse(config.getBoolean("web-panel.allow-external"));
        assertTrue(plugin.contains("permissions.amgui.backup.restore"));
        assertTrue(plugin.contains("permissions.amgui.cases.export"));
        assertTrue(plugin.contains("permissions.amgui.automod.approve"));
        assertTrue(plugin.contains("permissions.amgui.report.delete"));
        assertTrue(plugin.contains("permissions.amgui.inbox.view"));
        assertTrue(plugin.contains("permissions.amgui.simulator"));
        assertTrue(plugin.contains("permissions.amgui.incident.snapshot"));
        assertTrue(plugin.contains("permissions.amgui.privacy.purge"));
        assertTrue(plugin.contains("permissions.amgui.permissions.edit"));
        assertEquals(2, config.getInt("runtime.executor.threads"));
        assertEquals(256, config.getInt("runtime.executor.queue-capacity"));
        assertEquals(24, config.getLong("workflow.inbox.stale-hours"));
        assertTrue(config.getBoolean("backups.require-signature"));
        assertEquals(30, config.getLong("privacy.raw-ip-retention-days"));
        assertEquals(20, config.getInt("privacy.max-raw-ip-history-per-player"));
        assertTrue(config.contains("web-panel.tokens"));
    }

    @Test
    void automodAndTranslationsContainRequiredSections() {
        YamlConfiguration automod = load("automod.yml");
        YamlConfiguration ru = load("messages_ru.yml");
        YamlConfiguration en = load("messages_en.yml");

        assertTrue(automod.contains("settings.monitor-mode"));
        assertTrue(automod.contains("settings.escalation.steps"));
        assertFalse(automod.getBoolean("settings.escalation.enabled"));
        assertTrue(automod.getStringList("settings.ignored-commands").contains("login"));
        assertTrue(automod.contains("rules"));
        assertEquals(128, automod.getInt("settings.limits.max-rules"));
        assertTrue(automod.contains("settings.regex.budget-micros"));
        assertTrue(automod.contains("settings.approval.max-pending"));
        assertTrue(ru.contains("messages.no-permission"));
        assertTrue(en.contains("messages.no-permission"));
        assertTrue(ru.contains("gui.dashboard.title"));
        assertTrue(en.contains("gui.dashboard.title"));
        assertTrue(ru.contains("gui.inbox.title"));
        assertTrue(en.contains("gui.automod-approvals.title"));
    }

    private static YamlConfiguration load(String name) {
        InputStream stream = ResourceConfigurationTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, () -> "Missing test resource: " + name);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
