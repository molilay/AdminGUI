package me.admin.gui.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationParityTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-zA-Z0-9_-]+%");

    @Test void russianAndEnglishKeysAndPlaceholdersStayInParity() {
        YamlConfiguration ru = load("messages_ru.yml");
        YamlConfiguration en = load("messages_en.yml");
        assertEquals(leafKeys(ru), leafKeys(en), "RU/EN localization leaf keys differ");
        for (String key : leafKeys(ru)) {
            Object ruValue = ru.get(key), enValue = en.get(key);
            if (ruValue instanceof String ruText && enValue instanceof String enText) {
                assertEquals(placeholders(ruText), placeholders(enText), "Placeholder mismatch at " + key);
            }
        }
        assertTrue(ru.contains("gui.inbox.legend"));
        assertTrue(en.contains("gui.incident.capture-confirm"));
        assertTrue(ru.contains("common.actions.home"));
    }

    private static Set<String> leafKeys(YamlConfiguration yaml) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : yaml.getKeys(true)) if (!yaml.isConfigurationSection(key)) result.add(key);
        return result;
    }
    private static Set<String> placeholders(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
    private static YamlConfiguration load(String name) {
        InputStream stream = LocalizationParityTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
