package me.admin.gui.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditIntegrityTest {

    @TempDir
    Path temporary;

    @Test
    void detectsChangedPayloadAndBrokenPreviousHash() throws Exception {
        UUID firstId = UUID.randomUUID();
        String firstPayload = payload(firstId, 10L, "GENESIS", "ban", "reason one");
        String firstHash = AuditManager.sha256(firstPayload);
        String secondPayload = payload(UUID.randomUUID(), 20L, firstHash, "kick", "reason two");
        String secondHash = AuditManager.sha256(secondPayload);
        Path valid = temporary.resolve("valid.log");
        Files.writeString(valid, firstPayload + "|" + firstHash + "\n" + secondPayload + "|" + secondHash + "\n");
        AuditManager.ChainVerification verification = AuditManager.verifyFile(valid);
        assertTrue(verification.valid(), verification.reason());
        assertEquals(2, verification.entries());

        Path changed = temporary.resolve("changed.log");
        Files.writeString(changed, Files.readString(valid).replace("cmVhc29uIG9uZQ", "dGFtcGVyZWQ"));
        assertFalse(AuditManager.verifyFile(changed).valid());

        Path brokenChain = temporary.resolve("broken.log");
        String wrongPrevious = payload(UUID.randomUUID(), 20L, "GENESIS", "kick", "reason two");
        Files.writeString(brokenChain, firstPayload + "|" + firstHash + "\n" + wrongPrevious + "|"
                + AuditManager.sha256(wrongPrevious) + "\n");
        assertFalse(AuditManager.verifyFile(brokenChain).valid());
    }

    private static String payload(UUID id, long timestamp, String previous, String action, String details) {
        return String.join("|", "1", id.toString(), Long.toString(timestamp), "", encode("System"), encode(action),
                "", encode("target"), encode(details), previous);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
