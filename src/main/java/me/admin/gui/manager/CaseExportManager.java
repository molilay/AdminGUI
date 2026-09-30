package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports a self-verifiable case dossier using YAML-backed managers only. */
public final class CaseExportManager {

    public record ExportResult(Path file, String sha256, int entries) {}
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);
    private final AdvancedModeratorGUI plugin;

    public CaseExportManager(AdvancedModeratorGUI plugin) { this.plugin = plugin; }

    public CompletableFuture<ExportResult> exportAsync(int caseId) {
        ModerationCaseManager.ModerationCase moderationCase = plugin.getModerationCaseManager().get(caseId);
        if (moderationCase == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Дело не найдено"));
        Snapshot snapshot = snapshot(moderationCase);
        return CompletableFuture.supplyAsync(() -> {
            try {
                int auditLimit = Math.clamp(plugin.getConfig().getInt("moderation-cases.export-audit-limit", 10_000), 100, 50_000);
                List<AuditManager.AuditEntry> fullAudit = plugin.getAuditManager().findForExport(
                        moderationCase.targetUuid(), moderationCase.id(), auditLimit);
                return write(new Snapshot(snapshot.moderationCase(), snapshot.evidence(), snapshot.reports(), fullAudit));
            }
            catch (IOException e) { throw new java.util.concurrent.CompletionException(e); }
        });
    }

    private Snapshot snapshot(ModerationCaseManager.ModerationCase moderationCase) {
        List<EvidenceManager.EvidenceEntry> evidence = plugin.getEvidenceManager().getAllEvidence().stream()
                .filter(value -> moderationCase.evidenceIds().contains(value.id())).toList();
        List<ReportManager.ReportEntry> reports = moderationCase.reportIds().stream().map(plugin.getReportManager()::get)
                .filter(java.util.Objects::nonNull).toList();
        return new Snapshot(moderationCase, evidence, reports, List.of());
    }

    private ExportResult write(Snapshot snapshot) throws IOException {
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path directory = root.resolve("case-exports").normalize();
        if (!directory.startsWith(root)) throw new IOException("Некорректный каталог экспорта");
        Files.createDirectories(directory);
        String safeTarget = snapshot.moderationCase().targetName().replaceAll("[^A-Za-zА-Яа-я0-9_-]", "_");
        Path destination = directory.resolve("case-" + snapshot.moderationCase().id() + "-" + safeTarget + "-"
                + FILE_TIME.format(Instant.now()) + ".zip");
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("case.json", caseJson(snapshot.moderationCase()).getBytes(StandardCharsets.UTF_8));
        entries.put("evidence.json", evidenceJson(snapshot.evidence()).getBytes(StandardCharsets.UTF_8));
        entries.put("reports.json", reportsJson(snapshot.reports()).getBytes(StandardCharsets.UTF_8));
        entries.put("audit.json", auditJson(snapshot.audit()).getBytes(StandardCharsets.UTF_8));
        StringBuilder manifest = new StringBuilder("# SHA-256 manifest\n");
        entries.forEach((name, bytes) -> manifest.append(sha256(bytes)).append("  ").append(name).append('\n'));
        entries.put("manifest.sha256", manifest.toString().getBytes(StandardCharsets.UTF_8));

        try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(snapshot.moderationCase().updatedAt());
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
        return new ExportResult(destination, sha256(Files.readAllBytes(destination)), entries.size());
    }

    private static String caseJson(ModerationCaseManager.ModerationCase c) {
        return "{\n" + field("id", Integer.toString(c.id()), false) + field("target", c.targetName(), true)
                + field("targetUuid", c.targetUuid() == null ? "" : c.targetUuid().toString(), true)
                + field("title", c.title(), true) + field("createdBy", c.createdBy(), true)
                + field("assignedTo", c.assignedTo(), true) + field("status", c.status().name(), true)
                + field("priority", c.priority().name(), true) + field("createdAt", Long.toString(c.createdAt()), false)
                + field("updatedAt", Long.toString(c.updatedAt()), false) + field("dueAt", Long.toString(c.dueAt()), false)
                + "  \"events\": [\n" + join(c.events().stream().map(event -> "    {\"timestamp\":" + event.timestamp()
                + ",\"actor\":\"" + json(event.actor()) + "\",\"type\":\"" + json(event.type())
                + "\",\"details\":\"" + json(event.details()) + "\"}").toList()) + "\n  ]\n}\n";
    }

    private String evidenceJson(List<EvidenceManager.EvidenceEntry> values) {
        return "[\n" + join(values.stream().map(value -> "  {\"id\":" + value.id() + ",\"submitter\":\""
                + json(value.submitter()) + "\",\"description\":\"" + json(value.description()) + "\",\"text\":\""
                + json(value.evidenceText()) + "\",\"timestamp\":" + value.timestamp() + ",\"sha256\":\""
                + value.sha256() + "\",\"hashVersion\":" + value.hashVersion() + ",\"integrity\":\""
                + plugin.getEvidenceManager().verifyEvidence(value).name()
                + "\",\"removed\":" + value.removed() + ",\"removedBy\":\"" + json(value.removedBy())
                + "\",\"removedAt\":" + value.removedAt() + ",\"removalSha256\":\""
                + json(value.removalHash()) + "\"}").toList()) + "\n]\n";
    }

    private static String reportsJson(List<ReportManager.ReportEntry> values) {
        return "[\n" + join(values.stream().map(value -> "  {\"id\":" + value.id() + ",\"reporter\":\""
                + json(value.reporter()) + "\",\"category\":\"" + json(value.category()) + "\",\"reason\":\""
                + json(value.reason()) + "\",\"timestamp\":" + value.timestamp() + ",\"resolved\":" + value.resolved() + "}").toList()) + "\n]\n";
    }

    private static String auditJson(List<AuditManager.AuditEntry> values) {
        List<AuditManager.AuditEntry> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparingLong(AuditManager.AuditEntry::timestamp));
        return "[\n" + join(ordered.stream().map(value -> "  {\"id\":\"" + value.id() + "\",\"timestamp\":"
                + value.timestamp() + ",\"actor\":\"" + json(value.actorName()) + "\",\"action\":\""
                + json(value.action()) + "\",\"details\":\"" + json(value.details()) + "\",\"hash\":\""
                + value.hash() + "\"}").toList()) + "\n]\n";
    }

    private static String field(String name, String value, boolean string) {
        return "  \"" + name + "\": " + (string ? "\"" + json(value) + "\"" : value) + ",\n";
    }
    private static String join(List<String> values) { return String.join(",\n", values); }
    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    static String sha256(byte[] value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Snapshot(ModerationCaseManager.ModerationCase moderationCase, List<EvidenceManager.EvidenceEntry> evidence,
                            List<ReportManager.ReportEntry> reports, List<AuditManager.AuditEntry> audit) {}
}
