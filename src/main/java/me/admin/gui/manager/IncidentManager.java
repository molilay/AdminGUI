package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds a useful incident snapshot/timeline exclusively from Bukkit state and YAML-backed managers. */
public final class IncidentManager {
    public record TimelineEntry(long timestamp, String source, String actor, String details) { }
    public record SnapshotResult(long timestamp, File file, int cases, int messages, int evidence, int inventorySnapshots) { }

    private final AdvancedModeratorGUI plugin;
    private final File directory;

    public IncidentManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "incident-snapshots");
        if (!directory.exists() && !directory.mkdirs()) plugin.getLogger().warning("Cannot create incident-snapshots directory");
    }

    /** Must be invoked on the server thread because it snapshots live Bukkit state. */
    public SnapshotResult capture(OfflinePlayer target, String actor) {
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        List<ModerationCaseManager.ModerationCase> cases = plugin.getModerationCaseManager().getAll().stream()
                .filter(value -> uuid.equals(value.targetUuid()) || target.getName() != null && value.targetName().equalsIgnoreCase(target.getName())).toList();
        List<ChatHistoryManager.ChatMessage> chat = List.copyOf(plugin.getChatHistoryManager().getHistory(uuid));
        List<EvidenceManager.EvidenceEntry> evidence = plugin.getEvidenceManager().getAllEvidence().stream()
                .filter(value -> uuid.equals(value.targetUuid()) && !value.removed()).toList();
        List<InventoryRollbackManager.SnapshotInfo> inventories = plugin.getInventoryRollbackManager().getSnapshots(uuid);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("captured-at", now);
        yaml.set("captured-by", actor);
        yaml.set("target.uuid", uuid.toString());
        yaml.set("target.name", target.getName() == null ? "unknown" : target.getName());
        yaml.set("target.first-played", target.getFirstPlayed());
        yaml.set("target.last-played", target.getLastPlayed());
        yaml.set("target.flag", plugin.getFlagManager().getFlag(uuid));
        Player online = target.getPlayer();
        if (online != null) {
            yaml.set("live.world", online.getWorld().getName());
            yaml.set("live.x", Math.floor(online.getLocation().getX()));
            yaml.set("live.y", Math.floor(online.getLocation().getY()));
            yaml.set("live.z", Math.floor(online.getLocation().getZ()));
            yaml.set("live.ping", online.getPing());
            yaml.set("live.client-brand", online.getClientBrandName());
            yaml.set("live.health", online.getHealth());
            yaml.set("live.game-mode", online.getGameMode().name());
        }
        yaml.set("summary.cases", cases.size());
        yaml.set("summary.chat-messages", chat.size());
        yaml.set("summary.evidence", evidence.size());
        yaml.set("summary.inventory-snapshots", inventories.size());
        yaml.set("cases", cases.stream().limit(100).map(value -> "#" + value.id() + " " + value.status() + " " + value.title()).toList());
        yaml.set("recent-chat", chat.stream().skip(Math.max(0, chat.size() - 50))
                .map(value -> value.timestamp() + "|" + (value.isCommand() ? "COMMAND" : "CHAT") + "|" + sanitize(value.message())).toList());
        yaml.set("evidence", evidence.stream().limit(100)
                .map(value -> value.timestamp() + "|#" + value.id() + "|" + sanitize(value.description()) + "|" + value.integrityStatus()).toList());
        yaml.set("inventory-snapshots", inventories.stream().limit(50)
                .map(value -> value.timestamp() + "|" + sanitize(value.reason())).toList());

        File file = new File(directory, uuid + "-" + now + ".yml");
        YamlPersistenceService.saveYamlNow(plugin, file, yaml, "incident snapshot");
        plugin.getAuditManager().record(null, actor, "incident.snapshot", target.getName(), uuid,
                "file=" + file.getName() + "; cases=" + cases.size() + "; chat=" + chat.size()
                        + "; evidence=" + evidence.size() + "; database=false");
        return new SnapshotResult(now, file, cases.size(), chat.size(), evidence.size(), inventories.size());
    }

    public List<TimelineEntry> timeline(OfflinePlayer target, int limit) {
        UUID uuid = target.getUniqueId();
        String name = target.getName() == null ? "" : target.getName();
        List<TimelineEntry> result = new ArrayList<>();
        plugin.getModerationCaseManager().getAll().stream()
                .filter(value -> uuid.equals(value.targetUuid()) || value.targetName().equalsIgnoreCase(name))
                .forEach(value -> value.events().forEach(event -> result.add(new TimelineEntry(event.timestamp(),
                        "case #" + value.id(), event.actor(), event.type() + ": " + event.details()))));
        plugin.getChatHistoryManager().getHistory(uuid).forEach(message -> result.add(new TimelineEntry(message.timestamp(),
                message.isCommand() ? "command" : "chat", name, sanitize(message.message()))));
        plugin.getEvidenceManager().getAllEvidence().stream().filter(value -> uuid.equals(value.targetUuid()))
                .forEach(value -> result.add(new TimelineEntry(value.timestamp(), "evidence #" + value.id(), value.submitter(),
                        value.description() + " [" + value.integrityStatus() + "]")));
        plugin.getInventoryRollbackManager().getSnapshots(uuid).forEach(snapshot -> result.add(new TimelineEntry(snapshot.timestamp(),
                "inventory", "System", snapshot.reason())));
        plugin.getAuditManager().getRecent().stream().filter(entry -> uuid.equals(entry.targetUuid())
                        || !name.isBlank() && entry.targetName() != null && entry.targetName().equalsIgnoreCase(name))
                .forEach(entry -> result.add(new TimelineEntry(entry.timestamp(), "audit", entry.actorName(),
                        entry.action() + ": " + entry.details())));
        result.sort(Comparator.comparingLong(TimelineEntry::timestamp).reversed());
        return List.copyOf(result.subList(0, Math.min(Math.clamp(limit, 1, 1000), result.size())));
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
        return clean.length() <= 512 ? clean : clean.substring(0, 512);
    }
}
