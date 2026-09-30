package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceIntegrityTest {

    @Test
    void v2HashProtectsIdentityAndTombstoneFields() {
        EvidenceManager.EvidenceEntry original = new EvidenceManager.EvidenceEntry(
                7, "Player", UUID.randomUUID(), "Moderator", "Cheat proof", "clip://123", 123456L);
        assertEquals(EvidenceManager.IntegrityStatus.VALID, EvidenceManager.verifyIntegrity(original));

        EvidenceManager.EvidenceEntry changedTarget = new EvidenceManager.EvidenceEntry(original.id(), "OtherPlayer",
                original.targetUuid(), original.submitter(), original.description(), original.evidenceText(), original.timestamp(),
                original.sha256(), original.removed(), original.removedBy(), original.removedAt(), original.removalHash(),
                original.integrityStatus(), original.hashVersion());
        assertEquals(EvidenceManager.IntegrityStatus.INVALID, EvidenceManager.verifyIntegrity(changedTarget));

        EvidenceManager.EvidenceEntry forgedRemoval = new EvidenceManager.EvidenceEntry(original.id(), original.targetName(),
                original.targetUuid(), original.submitter(), original.description(), original.evidenceText(), original.timestamp(),
                original.sha256(), true, "attacker", 999L, "not-a-valid-tombstone", original.integrityStatus(), original.hashVersion());
        assertEquals(EvidenceManager.IntegrityStatus.INVALID, EvidenceManager.verifyIntegrity(forgedRemoval));
    }
}
