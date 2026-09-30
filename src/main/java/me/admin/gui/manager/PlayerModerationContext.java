package me.admin.gui.manager;

import java.util.Locale;
import java.util.UUID;

/**
 * Immutable data that may safely cross from the Bukkit main thread to an
 * asynchronous chat event. It intentionally contains no Bukkit/LuckPerms
 * object references.
 */
public record PlayerModerationContext(UUID uuid, String name, String world,
                                      String primaryGroup, boolean autoModBypass,
                                      boolean antiRaidBypass, long capturedAtMillis) {

    public PlayerModerationContext {
        if (uuid == null) throw new IllegalArgumentException("uuid");
        name = safe(name);
        world = safe(world).toLowerCase(Locale.ROOT);
        primaryGroup = safe(primaryGroup).toLowerCase(Locale.ROOT);
    }

    /** Missing cache entries are deliberately restrictive, never bypassed. */
    public static PlayerModerationContext restrictive(UUID uuid) {
        return new PlayerModerationContext(uuid, uuid.toString(), "", "default", false, false, 0L);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
