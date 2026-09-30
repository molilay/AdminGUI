package me.admin.gui.config;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class LocalizationManager {

    private final AdvancedModeratorGUI plugin;
    private YamlConfiguration messages;
    private YamlConfiguration fallback;
    private String language;

    public LocalizationManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ensureBundled("ru");
        ensureBundled("en");
        language = sanitizeLanguage(plugin.getConfig().getString("language", "ru"));
        File selected = new File(plugin.getDataFolder(), "messages_" + language + ".yml");
        if (!selected.exists()) {
            plugin.getLogger().warning("Файл локализации " + selected.getName() + " не найден; используется русский язык.");
            language = "ru";
            selected = new File(plugin.getDataFolder(), "messages_ru.yml");
        }
        messages = YamlConfiguration.loadConfiguration(selected);
        fallback = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_ru.yml"));
    }

    private void ensureBundled(String code) {
        File file = new File(plugin.getDataFolder(), "messages_" + code + ".yml");
        if (!file.exists()) plugin.saveResource(file.getName(), false);
    }

    private static String sanitizeLanguage(String value) {
        if (value == null) return "ru";
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.isBlank() ? "ru" : normalized;
    }

    public String get(String path, String defaultValue) {
        String value = messages.getString(path);
        if (value == null) value = fallback.getString(path, defaultValue);
        return color(value != null ? value : defaultValue);
    }

    public boolean contains(String path) {
        return messages.contains(path) || fallback.contains(path);
    }

    public List<String> getList(String path) {
        List<String> values = messages.getStringList(path);
        if (values.isEmpty()) values = fallback.getStringList(path);
        return values.stream().map(LocalizationManager::color).toList();
    }

    public String format(String path, String defaultValue, String... replacements) {
        String result = get(path, defaultValue);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return result;
    }

    public String getLanguage() {
        return language;
    }

    public String enumLabel(String group, Enum<?> value) {
        if (value == null) return get("common.unknown", "Неизвестно");
        String key = "enums." + group + "." + value.name().toLowerCase(Locale.ROOT);
        String fallbackValue = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return get(key, fallbackValue);
    }

    public String yesNo(boolean value) {
        return get(value ? "common.yes" : "common.no", value ? "да" : "нет");
    }

    public String formatDuration(long seconds) {
        long safe = Math.max(0, seconds);
        long days = safe / 86_400;
        long hours = safe % 86_400 / 3_600;
        long minutes = safe % 3_600 / 60;
        long secs = safe % 60;
        StringBuilder result = new StringBuilder();
        appendUnit(result, days, "time.days.short", "д");
        appendUnit(result, hours, "time.hours.short", "ч");
        appendUnit(result, minutes, "time.minutes.short", "м");
        if (result.isEmpty() || secs > 0) appendUnit(result, secs, "time.seconds.short", "с");
        return result.toString().trim();
    }

    private void appendUnit(StringBuilder result, long value, String key, String fallbackValue) {
        if (value <= 0) return;
        if (!result.isEmpty()) result.append(' ');
        result.append(value).append(get(key, fallbackValue));
    }

    public static String color(String value) {
        return value == null ? "" : value.replace('&', '§');
    }
}
