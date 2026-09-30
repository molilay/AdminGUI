package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import me.admin.gui.gui.PaginatedGUI;
import me.admin.gui.utils.ItemBuilder;
import me.admin.gui.utils.TimeUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class EvidenceManager {

    private final AdvancedModeratorGUI plugin;
    private final File dataFile;
    private final YamlPersistenceService persistence;
    private final ArtifactSignatureService signatures;
    private final Map<Integer, EvidenceEntry> evidence = new LinkedHashMap<>();
    private final Map<Integer, SignatureRecord> evidenceSignatures = new HashMap<>();
    private int nextId = 1;

    public enum IntegrityStatus { VALID, INVALID, LEGACY_UNSIGNED }
    private record SignatureRecord(String keyId, String hmac) {}

    public record EvidenceEntry(int id, String targetName, UUID targetUuid, String submitter, String description,
                                String evidenceText, long timestamp, String sha256, boolean removed, String removedBy,
                                long removedAt, String removalHash, IntegrityStatus integrityStatus, int hashVersion) {
        public EvidenceEntry(int id, String targetName, UUID targetUuid, String submitter, String description,
                             String evidenceText, long timestamp) {
            this(id, targetName, targetUuid, submitter, description, evidenceText, timestamp,
                    hashV2(id, targetName, targetUuid, submitter, description, evidenceText, timestamp), false, "", 0,
                    "", IntegrityStatus.VALID, 2);
        }
        public String getFormattedDate() { return TimeUtils.formatLogTime(timestamp); }
        public boolean integrityValid() { return integrityStatus != IntegrityStatus.INVALID; }
    }

    public EvidenceManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "evidence.yml");
        this.persistence = YamlPersistenceService.forPlugin(plugin);
        this.signatures = new ArtifactSignatureService(plugin);
        load();
    }

    public int addEvidence(String targetName, UUID targetUuid, String submitter, String description, String evidenceText) {
        EvidenceEntry entry = new EvidenceEntry(nextId, targetName, targetUuid, submitter, description, evidenceText, System.currentTimeMillis());
        evidence.put(nextId, entry);
        sign(entry);
        plugin.getAuditManager().record(null, submitter, "evidence.add", targetName, targetUuid,
                "evidence=" + entry.id() + "; " + description);
        plugin.getModerationCaseManager().attachEvidenceToLatestOpen(targetName, entry.id(), submitter);
        nextId++;
        save();
        return entry.id();
    }

    public List<EvidenceEntry> getEvidenceFor(UUID targetUuid) {
        return evidence.values().stream()
                .filter(e -> !e.removed() && e.targetUuid().equals(targetUuid))
                .sorted(Comparator.comparingLong(EvidenceEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<EvidenceEntry> getAllEvidence() {
        return evidence.values().stream()
                .sorted(Comparator.comparingLong(EvidenceEntry::timestamp).reversed())
                .collect(Collectors.toList());
    }

    public void remove(int id) {
        remove(id, "System");
    }

    public void remove(int id, String actor) {
        EvidenceEntry entry = evidence.get(id);
        if (entry != null && !entry.removed()) {
            long removedAt = System.currentTimeMillis();
            String removalHash = removalHash(entry.sha256(), actor, removedAt);
            evidence.put(id, new EvidenceEntry(entry.id(), entry.targetName(), entry.targetUuid(), entry.submitter(),
                    entry.description(), entry.evidenceText(), entry.timestamp(), entry.sha256(), true, actor, removedAt,
                    removalHash, verifyContent(entry) ? IntegrityStatus.VALID : IntegrityStatus.INVALID, entry.hashVersion()));
            sign(evidence.get(id));
            plugin.getAuditManager().record(null, actor, "evidence.remove", entry.targetName(), entry.targetUuid(),
                    "evidence=" + id + "; tombstone=" + removalHash);
        }
        save();
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (EvidenceEntry e : evidence.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("target", e.targetName());
            m.put("uuid", e.targetUuid().toString());
            m.put("submitter", e.submitter());
            m.put("description", e.description());
            m.put("evidence", e.evidenceText());
            m.put("timestamp", e.timestamp());
            m.put("sha256", e.sha256());
            m.put("hash-version", e.hashVersion());
            m.put("removed", e.removed());
            m.put("removed-by", e.removedBy());
            m.put("removed-at", e.removedAt());
            m.put("removal-sha256", e.removalHash());
            SignatureRecord signature = evidenceSignatures.get(e.id());
            if (signature != null) {
                m.put("hmac-key-id", signature.keyId());
                m.put("hmac-sha256", signature.hmac());
            }
            m.put("integrity", verifySigned(e).name());
            list.add(m);
        }
        config.set("evidence", list);
        if (!persistence.save(dataFile.toPath(), config.saveToString()))
            plugin.getLogger().warning("Failed to enqueue evidence.yml save");
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        nextId = config.getInt("next-id", 1);
        List<Map<?, ?>> raw = config.getMapList("evidence");
        boolean migrated = false;
        int invalid = 0;
        for (Map<?, ?> m : raw) {
            try {
                int id = ((Number) m.get("id")).intValue();
                UUID uuid = UUID.fromString(String.valueOf(m.get("uuid")));
                String submitter = String.valueOf(m.get("submitter"));
                String description = String.valueOf(m.get("description"));
                String text = String.valueOf(m.get("evidence"));
                long timestamp = ((Number) m.get("timestamp")).longValue();
                String targetName = String.valueOf(m.get("target"));
                int hashVersion = m.get("sha256") == null ? 2
                        : m.get("hash-version") instanceof Number number ? number.intValue() : 1;
                String calculated = hashVersion >= 2 ? hashV2(id, targetName, uuid, submitter, description, text, timestamp)
                        : legacyHash(uuid, submitter, description, text, timestamp);
                String stored = m.get("sha256") == null ? calculated : String.valueOf(m.get("sha256"));
                boolean removed = Boolean.TRUE.equals(m.get("removed"));
                String removedBy = m.get("removed-by") == null ? "" : String.valueOf(m.get("removed-by"));
                long removedAt = m.get("removed-at") instanceof Number number ? number.longValue() : 0L;
                String calculatedRemoval = removed ? removalHash(stored, removedBy, removedAt) : "";
                String storedRemoval = m.get("removal-sha256") == null ? calculatedRemoval : String.valueOf(m.get("removal-sha256"));
                IntegrityStatus status = constantTimeEquals(stored, calculated)
                        && (!removed || constantTimeEquals(storedRemoval, calculatedRemoval))
                        ? IntegrityStatus.VALID : IntegrityStatus.INVALID;
                String keyId = m.get("hmac-key-id") == null ? "" : String.valueOf(m.get("hmac-key-id"));
                String hmac = m.get("hmac-sha256") == null ? "" : String.valueOf(m.get("hmac-sha256"));
                if (m.get("sha256") == null || removed && m.get("removal-sha256") == null) migrated = true;
                EvidenceEntry loaded = new EvidenceEntry(id, targetName, uuid, submitter,
                        description, text, timestamp, stored, removed, removedBy, removedAt, storedRemoval, status, hashVersion);
                if (status != IntegrityStatus.INVALID && !hmac.isBlank()) {
                    evidenceSignatures.put(id, new SignatureRecord(keyId, hmac));
                    if (!signatures.keyId().equals(keyId) || !signatures.verify(signaturePayload(loaded), hmac)) {
                        status = IntegrityStatus.INVALID;
                    }
                } else if (status != IntegrityStatus.INVALID) status = IntegrityStatus.LEGACY_UNSIGNED;
                if (status == IntegrityStatus.INVALID) invalid++;
                evidence.put(id, new EvidenceEntry(id, targetName, uuid, submitter,
                        description, text, timestamp, stored, removed, removedBy, removedAt, storedRemoval, status, hashVersion));
            } catch (Exception ignored) {}
        }
        if (invalid > 0) plugin.getLogger().severe("Evidence integrity check failed for " + invalid + " entries in evidence.yml");
        if (migrated) save();
    }

    public static class EvidenceGUI extends PaginatedGUI {
        private final UUID targetUuid;
        private final String targetName;
        private List<EvidenceEntry> entries;

        public EvidenceGUI(AdvancedModeratorGUI plugin, Player viewer, UUID targetUuid, String targetName) {
            super(plugin, viewer);
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override public void open() {
            if (!viewer.hasPermission("amgui.evidence")) {
                viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return;
            }
            super.open();
        }

        @Override
        public String getTitle() { return "§8Улики: " + targetName; }

        @Override
        public void buildContent() {
            contentItems.clear();
            entries = plugin.getEvidenceManager().getEvidenceFor(targetUuid);
            for (EvidenceEntry e : entries) {
                contentItems.add(new ItemBuilder(Material.PAPER)
                        .name("&6#" + e.id() + " &f" + e.description())
                        .lore(
                                "&7Добавил: &f" + e.submitter(),
                                "&7Текст: &f" + e.evidenceText(),
                                "&7Время: &f" + e.getFormattedDate(),
                                "&7SHA-256: &8" + e.sha256().substring(0, Math.min(16, e.sha256().length())) + "…",
                                e.integrityStatus() == IntegrityStatus.VALID ? "&aAuthenticity: SIGNED"
                                        : e.integrityStatus() == IntegrityStatus.LEGACY_UNSIGNED
                                        ? "&eAuthenticity: LEGACY_UNSIGNED" : "&cAuthenticity: INVALID",
                                "",
                                viewer.hasPermission("amgui.evidence.remove") ? "&cПКМ — удалить" : "&8Удаление недоступно"
                        ).build());
            }
        }

        @Override
        public void onClick(int slot) {
            onClick(slot, false, false);
        }

        @Override
        public void onClick(int slot, boolean shift, boolean right) {
            if (slot >= 45) { handlePaginatedClick(slot); return; }
            if (slot == SLOT_MAIN_MENU) {
                plugin.getGuiManager().unregister(viewer.getUniqueId());
                new me.admin.gui.gui.MainMenu(plugin, viewer).open();
                return;
            }
            int index = page * MAX_ITEMS_PER_PAGE + slot;
            if (index < 0 || index >= entries.size()) return;
            if (right || shift) {
                if (!viewer.hasPermission("amgui.evidence.remove")) {
                    viewer.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return;
                }
                EvidenceEntry e = entries.get(index);
                new me.admin.gui.gui.ConfirmGUI(plugin, viewer, "§cУдалить улику #" + e.id() + "?", () -> {
                    if (!viewer.hasPermission("amgui.evidence.remove")) return;
                    plugin.getEvidenceManager().remove(e.id(), viewer.getName());
                    viewer.sendMessage("§c✓ Улика #" + e.id() + " удалена.");
                    refresh();
                }).open();
            }
        }
    }

    public IntegrityStatus verifyEvidence(EvidenceEntry entry) { return verifySigned(entry); }
    static IntegrityStatus verifyIntegrity(EvidenceEntry entry) { return verify(entry); }

    public long invalidEvidenceCount() {
        return evidence.values().stream().filter(entry -> verifySigned(entry) == IntegrityStatus.INVALID).count();
    }

    public long unsignedEvidenceCount() {
        return evidence.values().stream().filter(entry -> verifySigned(entry) == IntegrityStatus.LEGACY_UNSIGNED).count();
    }

    public void flush() { persistence.flush(); }

    private static IntegrityStatus verify(EvidenceEntry entry) {
        if (!verifyContent(entry)) return IntegrityStatus.INVALID;
        if (entry.removed() && !constantTimeEquals(entry.removalHash(),
                removalHash(entry.sha256(), entry.removedBy(), entry.removedAt()))) return IntegrityStatus.INVALID;
        return IntegrityStatus.VALID;
    }

    private IntegrityStatus verifySigned(EvidenceEntry entry) {
        if (verify(entry) == IntegrityStatus.INVALID) return IntegrityStatus.INVALID;
        SignatureRecord signature = evidenceSignatures.get(entry.id());
        if (signature == null) return IntegrityStatus.LEGACY_UNSIGNED;
        return signatures.keyId().equals(signature.keyId()) && signatures.verify(signaturePayload(entry), signature.hmac())
                ? IntegrityStatus.VALID : IntegrityStatus.INVALID;
    }

    private void sign(EvidenceEntry entry) {
        evidenceSignatures.put(entry.id(), new SignatureRecord(signatures.keyId(), signatures.sign(signaturePayload(entry))));
    }

    private static byte[] signaturePayload(EvidenceEntry entry) {
        String value = "EVIDENCE-AUTH-V1" + canonical(Integer.toString(entry.id())) + canonical(entry.sha256())
                + canonical(Boolean.toString(entry.removed())) + canonical(entry.removedBy())
                + canonical(Long.toString(entry.removedAt())) + canonical(entry.removalHash());
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean verifyContent(EvidenceEntry entry) {
        String calculated = entry.hashVersion() >= 2
                ? hashV2(entry.id(), entry.targetName(), entry.targetUuid(), entry.submitter(), entry.description(),
                        entry.evidenceText(), entry.timestamp())
                : legacyHash(entry.targetUuid(), entry.submitter(), entry.description(), entry.evidenceText(), entry.timestamp());
        return constantTimeEquals(entry.sha256(), calculated);
    }

    private static String removalHash(String contentHash, String actor, long removedAt) {
        return digest("REMOVE|" + contentHash + "|" + (actor == null ? "" : actor) + "|" + removedAt);
    }

    private static String legacyHash(UUID targetUuid, String submitter, String description, String text, long timestamp) {
        return digest(targetUuid + "|" + submitter + "|" + description + "|" + text + "|" + timestamp);
    }

    private static String hashV2(int id, String targetName, UUID targetUuid, String submitter,
                                 String description, String text, long timestamp) {
        return digest("EVIDENCE-V2" + canonical(Integer.toString(id)) + canonical(targetName)
                + canonical(String.valueOf(targetUuid)) + canonical(submitter) + canonical(description)
                + canonical(text) + canonical(Long.toString(timestamp)));
    }

    private static String canonical(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe;
    }

    private static String digest(String payload) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) return false;
        return java.security.MessageDigest.isEqual(first.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                second.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
