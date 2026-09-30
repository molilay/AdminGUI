package me.admin.gui.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class YamlBackupManagerTest {

    @TempDir
    Path temporary;

    @Test
    void includesOnlyYamlAndAuditLog() {
        assertTrue(YamlBackupManager.shouldInclude(Path.of("config.yml")));
        assertTrue(YamlBackupManager.shouldInclude(Path.of("data", "cases.yaml")));
        assertTrue(YamlBackupManager.shouldInclude(Path.of("audit.log")));
        assertFalse(YamlBackupManager.shouldInclude(Path.of("moderation.db")));
        assertFalse(YamlBackupManager.shouldInclude(Path.of("database.sqlite")));
        assertFalse(YamlBackupManager.shouldInclude(Path.of("exports", "cases.yml")));
        assertFalse(YamlBackupManager.shouldInclude(Path.of("backups", "old.yml")));
        assertFalse(YamlBackupManager.shouldInclude(Path.of("pending-restore", "config.yml")));
        assertFalse(YamlBackupManager.shouldInclude(Path.of("pending-restore-rollback", "config.yml")));
    }

    @Test
    void verifiesPerFileManifestAndDetectsTampering() throws Exception {
        byte[] yaml = "enabled: true\n".getBytes(StandardCharsets.UTF_8);
        Path valid = temporary.resolve("valid.zip");
        writeZip(valid, Map.of("config.yml", yaml), Map.of("config.yml", sha256(yaml)));
        YamlBackupManager.BackupVerification validResult = YamlBackupManager.verifyArchive(valid);
        assertTrue(validResult.valid(), () -> validResult.errors().toString());
        assertEquals(1, validResult.entries());

        Path tampered = temporary.resolve("tampered.zip");
        writeZip(tampered, Map.of("config.yml", "enabled: false\n".getBytes(StandardCharsets.UTF_8)),
                Map.of("config.yml", sha256(yaml)));
        YamlBackupManager.BackupVerification tamperedResult = YamlBackupManager.verifyArchive(tampered);
        assertFalse(tamperedResult.valid());
        assertTrue(tamperedResult.errors().stream().anyMatch(error -> error.contains("Hash mismatch")));
    }

    @Test
    void rejectsTraversalAndDuplicateManifestCoverage() throws Exception {
        byte[] yaml = "safe: true\n".getBytes(StandardCharsets.UTF_8);
        Path hostile = temporary.resolve("hostile.zip");
        writeZip(hostile, Map.of("../outside.yml", yaml), Map.of("../outside.yml", sha256(yaml)));
        assertFalse(YamlBackupManager.verifyArchive(hostile).valid());

        Path missingCoverage = temporary.resolve("missing-coverage.zip");
        writeZip(missingCoverage, Map.of("one.yml", yaml, "two.yml", yaml), Map.of("one.yml", sha256(yaml)));
        YamlBackupManager.BackupVerification result = YamlBackupManager.verifyArchive(missingCoverage);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("does not match")));
    }

    private static void writeZip(Path destination, Map<String, byte[]> entries, Map<String, String> manifest) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            StringBuilder value = new StringBuilder("# test manifest\n");
            manifest.forEach((name, hash) -> value.append(hash).append("  ").append(name).append('\n'));
            zip.putNextEntry(new ZipEntry("manifest.sha256"));
            zip.write(value.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
