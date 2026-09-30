package me.admin.gui.utils;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.compat.ServerCompat;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays GUI feedback sounds.
 *
 * <p>Sound names are taken from {@code sounds.<key>} in {@code config.yml}, resolved through
 * {@link ServerCompat} and cached, so an unknown or version-specific name degrades to the
 * built-in default instead of throwing. The whole helper honours {@code gui.sound-enabled}
 * and never throws — a missing plugin instance, a left player or a removed sound enum are
 * all silent no-ops.
 */
public final class SoundUtil {

    private static final Map<String, Optional<Sound>> RESOLVED = new ConcurrentHashMap<>();
    private static volatile Boolean enabled;

    private SoundUtil() {
    }

    /** Drops cached config values; call after {@code /amgui reload}. */
    public static void reload() {
        RESOLVED.clear();
        enabled = null;
    }

    public static void click(Player player) {
        play(player, "click", "UI_BUTTON_CLICK");
    }

    public static void success(Player player) {
        play(player, "success", "ENTITY_PLAYER_LEVELUP");
    }

    public static void error(Player player) {
        play(player, "error", "ENTITY_VILLAGER_NO");
    }

    /**
     * Plays a configured sound.
     *
     * @param player     target player, {@code null} is ignored
     * @param configKey  key inside the {@code sounds} section of {@code config.yml}
     * @param fallbacks  built-in sound names used when the configured one is missing
     */
    public static void play(Player player, String configKey, String... fallbacks) {
        if (player == null || !isEnabled()) return;
        Sound sound = resolve(configKey, fallbacks);
        if (sound == null) return;
        try {
            player.playSound(player, sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Sound removed in this server version or the player left mid-tick.
        }
    }

    /** Resolved sound or {@code null} when nothing can be played. Test-friendly. */
    public static Sound resolve(String configKey, String... fallbacks) {
        if (configKey == null || configKey.isBlank()) return null;
        String configured = configuredName(configKey, fallbacks);
        String cacheKey = configKey + "|" + configured;
        Optional<Sound> resolved = RESOLVED.computeIfAbsent(cacheKey,
                key -> ServerCompat.firstSound(candidates(configured, fallbacks)));
        return resolved == null ? null : resolved.orElse(null);
    }

    private static String[] candidates(String configured, String... fallbacks) {
        if (configured.isBlank()) return fallbacks == null ? new String[0] : fallbacks;
        if (fallbacks == null || fallbacks.length == 0) return new String[]{configured};
        String[] merged = new String[fallbacks.length + 1];
        merged[0] = configured;
        System.arraycopy(fallbacks, 0, merged, 1, fallbacks.length);
        return merged;
    }

    private static String configuredName(String configKey, String... fallbacks) {
        AdvancedModeratorGUI plugin = AdvancedModeratorGUI.getInstance();
        if (plugin != null && plugin.getConfigManager() != null) {
            try {
                String value = plugin.getConfigManager().getSound(configKey);
                if (value != null && !value.isBlank()) return value.trim();
            } catch (Throwable ignored) {
                // Config not ready yet — use the built-in default.
            }
        }
        return fallbacks != null && fallbacks.length > 0 ? fallbacks[0] : "";
    }

    private static boolean isEnabled() {
        Boolean cached = enabled;
        if (cached != null) return cached;
        boolean value = true;
        AdvancedModeratorGUI plugin = AdvancedModeratorGUI.getInstance();
        if (plugin != null) {
            try {
                value = plugin.getConfig().getBoolean("gui.sound-enabled", true);
            } catch (Throwable ignored) {
                value = true;
            }
        }
        enabled = value;
        return value;
    }
}
