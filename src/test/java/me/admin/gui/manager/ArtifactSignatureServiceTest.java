package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactSignatureServiceTest {

    @Test
    void hmacBindsPayloadAndKey() {
        byte[] firstKey = new byte[32];
        byte[] secondKey = new byte[32];
        secondKey[0] = 1;
        byte[] payload = "manifest".getBytes(StandardCharsets.UTF_8);
        assertEquals(ArtifactSignatureService.hmac(firstKey, payload), ArtifactSignatureService.hmac(firstKey, payload));
        assertNotEquals(ArtifactSignatureService.hmac(firstKey, payload), ArtifactSignatureService.hmac(secondKey, payload));
        assertNotEquals(ArtifactSignatureService.hmac(firstKey, payload),
                ArtifactSignatureService.hmac(firstKey, "tampered".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void envelopeRejectsChangedManifestAndForeignKey() {
        byte[] key = new byte[32];
        key[3] = 7;
        byte[] foreign = new byte[32];
        foreign[3] = 8;
        byte[] manifest = "abc  config.yml\n".getBytes(StandardCharsets.UTF_8);
        String envelope = ArtifactSignatureService.envelope(key, manifest);

        assertTrue(ArtifactSignatureService.verifyEnvelope(key, manifest, envelope));
        assertFalse(ArtifactSignatureService.verifyEnvelope(key,
                "def  config.yml\n".getBytes(StandardCharsets.UTF_8), envelope));
        assertFalse(ArtifactSignatureService.verifyEnvelope(foreign, manifest, envelope));
    }
}
