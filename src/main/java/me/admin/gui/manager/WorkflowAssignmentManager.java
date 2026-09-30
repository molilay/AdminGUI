package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small YAML-only ownership registry for inbox entities that have no native assignee field. */
public final class WorkflowAssignmentManager {
    public enum ClaimResult { CLAIMED, ALREADY_OWNED, CONFLICT, NOT_FOUND }

    private final AdvancedModeratorGUI plugin;
    private final File file;
    private final Map<String, String> owners = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> compactMode = new ConcurrentHashMap<>();

    public WorkflowAssignmentManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "workflow.yml");
        load();
    }

    public String owner(String entityKey) {
        return owners.getOrDefault(normalize(entityKey), "");
    }

    /** Atomically claims an entity. Takeover must be an explicit, confirmed caller decision. */
    public synchronized ClaimResult claim(String entityKey, String actor, String expectedOwner, boolean takeover, boolean exists) {
        if (!exists) return ClaimResult.NOT_FOUND;
        String key = normalize(entityKey);
        String current = owners.getOrDefault(key, "");
        ClaimResult decision = decideClaim(current, actor, expectedOwner, takeover);
        if (decision != ClaimResult.CLAIMED) return decision;
        owners.put(key, actor);
        save();
        return ClaimResult.CLAIMED;
    }

    public synchronized void release(String entityKey) {
        if (owners.remove(normalize(entityKey)) != null) save();
    }

    public boolean isCompact(UUID viewer) { return compactMode.getOrDefault(viewer, false); }

    public synchronized boolean toggleCompact(UUID viewer) {
        boolean enabled = !isCompact(viewer);
        compactMode.put(viewer, enabled);
        save();
        return enabled;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("owners");
        if (section != null) section.getKeys(false).forEach(key -> {
            String owner = section.getString(key, "").trim();
            if (!owner.isBlank()) owners.put(normalize(key), owner);
        });
        for (String raw : yaml.getStringList("compact-viewers")) {
            try { compactMode.put(UUID.fromString(raw), true); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        owners.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> yaml.set("owners." + entry.getKey(), entry.getValue()));
        yaml.set("compact-viewers", compactMode.entrySet().stream().filter(Map.Entry::getValue)
                .map(entry -> entry.getKey().toString()).sorted().toList());
        YamlPersistenceService.queueYaml(plugin, file, yaml, "workflow assignments");
    }

    private static String normalize(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]+:[0-9]+")) throw new IllegalArgumentException("invalid entity key");
        return key.toLowerCase(Locale.ROOT);
    }

    static ClaimResult decideClaim(String current, String actor, String expectedOwner, boolean takeover) {
        String actual = current == null ? "" : current;
        String expected = expectedOwner == null ? "" : expectedOwner;
        if (!actual.equalsIgnoreCase(expected)) return ClaimResult.CONFLICT;
        if (actual.equalsIgnoreCase(actor)) return ClaimResult.ALREADY_OWNED;
        if (!actual.isBlank() && !takeover) return ClaimResult.CONFLICT;
        return ClaimResult.CLAIMED;
    }
}
