package me.admin.gui.compat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime capability detection for the moderation plugin.
 *
 * <p>The plugin is compiled against the Paper API for Minecraft 1.21.4, therefore every
 * version- or platform-specific API access goes through this class:
 * <ul>
 *     <li>version and platform are detected once and cached (no repeated reflection);</li>
 *     <li>{@code Material} / {@code Sound} lookups never throw — an unknown name degrades
 *     to the caller supplied fallback;</li>
 *     <li>all detection is safe to call before {@code onEnable()} and on non-Paper servers.</li>
 * </ul>
 */
public final class ServerCompat {

    /** Lowest supported Minecraft version: 1.21.4. */
    public static final int MINIMUM_MAJOR = 1;
    public static final int MINIMUM_MINOR = 21;
    public static final int MINIMUM_PATCH = 4;

    private static final String MINIMUM_VERSION = MINIMUM_MAJOR + "." + MINIMUM_MINOR + "." + MINIMUM_PATCH;

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:[._-](\\d+))?");
    private static final Pattern MC_PREFIX = Pattern.compile("MC:\\s*([0-9][0-9._-]*)");

    private static final String CLASS_PAPER_BAN_API = "io.papermc.paper.ban.BanListType";
    private static final String CLASS_PAPER_PROFILE = "com.destroystokyo.paper.profile.PlayerProfile";
    private static final String CLASS_FOLIA = "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler";
    private static final String CLASS_SPIGOT = "org.spigotmc.SpigotConfig";
    private static final String CLASS_ADVENTURE = "net.kyori.adventure.text.Component";

    private static final Map<String, Boolean> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Material>> MATERIAL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Sound>> SOUND_CACHE = new ConcurrentHashMap<>();

    private static volatile Version cachedVersion;
    private static volatile Platform cachedPlatform;

    private ServerCompat() {
    }

    public enum Platform { PAPER, FOLIA, SPIGOT, UNKNOWN }

    /** Parsed server version. {@link #UNKNOWN} is used when the version cannot be detected. */
    public record Version(int major, int minor, int patch) implements Comparable<Version> {

        public static final Version UNKNOWN = new Version(0, 0, 0);

        /**
         * Parses versions out of {@code 1.21.4}, {@code 1.21.4-R0.1-SNAPSHOT},
         * {@code 1.21} and {@code git-Paper-196 (MC: 1.21.4)}.
         *
         * @return parsed version or {@code null} when nothing version-like is found
         */
        public static Version parse(String raw) {
            if (raw == null) return null;
            String text = raw.trim();
            if (text.isEmpty()) return null;
            Matcher prefixed = MC_PREFIX.matcher(text);
            if (prefixed.find()) text = prefixed.group(1);
            Matcher matcher = VERSION_PATTERN.matcher(text);
            if (!matcher.find()) return null;
            try {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                if (major < 0 || minor < 0 || patch < 0) return null;
                return new Version(major, minor, patch);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public boolean known() {
            return major > 0;
        }

        public boolean atLeast(int major, int minor, int patch) {
            return compareTo(new Version(major, minor, patch)) >= 0;
        }

        @Override
        public int compareTo(Version other) {
            if (other == null) return 1;
            if (major != other.major) return Integer.compare(major, other.major);
            if (minor != other.minor) return Integer.compare(minor, other.minor);
            return Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    /** Detected Minecraft version, never {@code null}. */
    public static Version version() {
        Version local = cachedVersion;
        if (local == null) {
            synchronized (ServerCompat.class) {
                local = cachedVersion;
                if (local == null) {
                    local = detectVersion();
                    cachedVersion = local;
                }
            }
        }
        return local;
    }

    /** Detected server platform, never {@code null}. */
    public static Platform platform() {
        Platform local = cachedPlatform;
        if (local == null) {
            synchronized (ServerCompat.class) {
                local = cachedPlatform;
                if (local == null) {
                    local = detectPlatform();
                    cachedPlatform = local;
                }
            }
        }
        return local;
    }

    public static boolean isPaper() {
        Platform detected = platform();
        return detected == Platform.PAPER || detected == Platform.FOLIA;
    }

    public static boolean isFolia() {
        return platform() == Platform.FOLIA;
    }

    /** Minimum supported version check (1.21.4). */
    public static boolean isSupportedVersion() {
        return version().atLeast(MINIMUM_MAJOR, MINIMUM_MINOR, MINIMUM_PATCH);
    }

    /** Fully supported runtime: Paper (not Folia) on 1.21.4 or newer. */
    public static boolean isSupported() {
        return isPaper() && !isFolia() && isSupportedVersion();
    }

    /** Cached, exception-free classpath probe. */
    public static boolean hasClass(String className) {
        if (className == null || className.isBlank()) return false;
        return CLASS_CACHE.computeIfAbsent(className, name -> {
            try {
                Class.forName(name);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        });
    }

    /** Paper ban API ({@code BanListType}) availability. */
    public static boolean supportsBanApi() {
        return hasClass(CLASS_PAPER_BAN_API);
    }

    /** Paper {@code PlayerProfile} availability (player heads). */
    public static boolean supportsPlayerProfile() {
        return hasClass(CLASS_PAPER_PROFILE);
    }

    /** Adventure availability (used for every GUI text component). */
    public static boolean supportsAdventure() {
        return hasClass(CLASS_ADVENTURE);
    }

    /**
     * Resolves a material name without throwing.
     *
     * @return resolved material or {@link Optional#empty()}
     */
    public static Optional<Material> material(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String key = name.trim().toUpperCase(Locale.ROOT);
        return MATERIAL_CACHE.computeIfAbsent(key, ServerCompat::lookupMaterial);
    }

    /** Resolves the first available material from the candidate list. */
    public static Optional<Material> firstMaterial(String... candidates) {
        return firstPresent(candidates, ServerCompat::material);
    }

    /**
     * Resolves the first available material from the candidate list.
     *
     * @param fallback returned when nothing resolves
     */
    public static Material material(Material fallback, String... candidates) {
        return firstMaterial(candidates).orElse(fallback);
    }

    /** Resolves a sound name without throwing. */
    public static Optional<Sound> sound(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String key = name.trim().toUpperCase(Locale.ROOT);
        return SOUND_CACHE.computeIfAbsent(key, ServerCompat::lookupSound);
    }

    /** Resolves the first available sound from the candidate list. */
    public static Optional<Sound> firstSound(String... candidates) {
        return firstPresent(candidates, ServerCompat::sound);
    }

    /** Resolves the first available sound from the candidate list. */
    public static Sound sound(Sound fallback, String... candidates) {
        return firstSound(candidates).orElse(fallback);
    }

    /**
     * Returns the first candidate the resolver can resolve. Pure helper: safe for unit tests
     * and never calls into Bukkit by itself.
     */
    public static <T> Optional<T> firstPresent(String[] candidates, Function<String, Optional<T>> resolver) {
        if (candidates == null || resolver == null) return Optional.empty();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            Optional<T> resolved;
            try {
                resolved = resolver.apply(candidate);
            } catch (RuntimeException ignored) {
                resolved = null;
            }
            if (resolved != null && resolved.isPresent()) return resolved;
        }
        return Optional.empty();
    }

    /** Human readable compatibility report for logs, {@code /amgui check} and Doctor. */
    public static List<String> compatibilityWarnings() {
        List<String> warnings = new ArrayList<>();
        Platform detected = platform();
        Version detectedVersion = version();

        switch (detected) {
            case UNKNOWN -> warnings.add("Платформа не определена: требуется Paper " + MINIMUM_VERSION + "+");
            case SPIGOT -> warnings.add("Обнаружен Spigot/CraftBukkit: Paper-only API (баны, профили, TPS) недоступны");
            case FOLIA -> warnings.add("Обнаружен Folia: плагин использует BukkitScheduler, поддержка не гарантируется");
            case PAPER -> { }
        }
        if (!detectedVersion.known()) {
            warnings.add("Версию сервера определить не удалось");
        } else if (!detectedVersion.atLeast(MINIMUM_MAJOR, MINIMUM_MINOR, MINIMUM_PATCH)) {
            warnings.add("Версия " + detectedVersion + " старше минимальной " + MINIMUM_VERSION);
        }
        if (!supportsBanApi()) warnings.add("API банов Paper недоступно (BanListType): списки банов будут пустыми");
        if (!supportsPlayerProfile()) warnings.add("PlayerProfile недоступен: кэш голов игроков отключён");
        if (!supportsAdventure()) warnings.add("Adventure API отсутствует: тексты GUI не будут отображаться");
        return List.copyOf(warnings);
    }

    /** Logs the detected runtime and every compatibility warning. */
    public static void report(Plugin plugin) {
        if (plugin == null) return;
        String line = "Платформа: " + platform() + ", Minecraft " + version()
                + " (Bukkit " + safeBukkitVersion() + ")";
        List<String> warnings = compatibilityWarnings();
        if (warnings.isEmpty()) {
            plugin.getLogger().info(line + " — окружение поддерживается.");
            return;
        }
        plugin.getLogger().warning(line + " — обнаружено проблем совместимости: " + warnings.size());
        for (String warning : warnings) {
            plugin.getLogger().warning(" - " + warning);
        }
    }

    /** Short one-line summary for {@code /amgui check}. */
    public static String describe() {
        return platform() + " " + version();
    }

    private static Version detectVersion() {
        Version parsed = Version.parse(readVersionString());
        return parsed == null ? Version.UNKNOWN : parsed;
    }

    private static String readVersionString() {
        // getMinecraftVersion() есть не во всех версиях API, поэтому вызывается через рефлексию.
        String raw = invokeStatic("getMinecraftVersion");
        if (isBlank(raw)) raw = invokeStatic("getBukkitVersion");
        if (isBlank(raw)) raw = invokeStatic("getVersion");
        return raw;
    }

    private static Platform detectPlatform() {
        if (hasClass(CLASS_FOLIA)) return Platform.FOLIA;
        if (hasClass(CLASS_PAPER_BAN_API) || hasClass(CLASS_PAPER_PROFILE)) return Platform.PAPER;
        if (hasClass(CLASS_SPIGOT)) return Platform.SPIGOT;
        return Platform.UNKNOWN;
    }

    private static Optional<Material> lookupMaterial(String key) {
        try {
            Material matched = Material.matchMaterial(key);
            if (matched != null) return Optional.of(matched);
        } catch (Throwable ignored) {
            // Older/newer servers may not expose the method — fall through to valueOf.
        }
        try {
            return Optional.ofNullable(Material.valueOf(key));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Sound> lookupSound(String key) {
        try {
            return Optional.ofNullable(Sound.valueOf(key));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    /** Вызов статического метода {@link Bukkit} без compile-time зависимости от версии API. */
    private static String invokeStatic(String methodName) {
        try {
            Method method = Bukkit.class.getMethod(methodName);
            Object value = method.invoke(null);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            // Метода нет в этой версии API или сервер ещё не инициализирован.
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeBukkitVersion() {
        String raw = invokeStatic("getBukkitVersion");
        return isBlank(raw) ? "?" : raw;
    }
}
