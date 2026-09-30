package me.admin.gui.manager.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthMeActionApprovalQueueTest {

    @Test
    void requiresDifferentSecondActorAndConsumesApproval() {
        AuthMeActionApprovalQueue queue = new AuthMeActionApprovalQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertEquals(AuthMeActionApprovalQueue.Status.REQUESTED,
                queue.requestOrApprove(first, target, AuthMeActionApprovalQueue.Action.FORCE_LOGIN,
                        1_000L, 60_000L).status());
        assertEquals(AuthMeActionApprovalQueue.Status.OWN_REQUEST,
                queue.requestOrApprove(first, target, AuthMeActionApprovalQueue.Action.FORCE_LOGIN,
                        2_000L, 60_000L).status());
        assertEquals(AuthMeActionApprovalQueue.Status.APPROVED,
                queue.requestOrApprove(second, target, AuthMeActionApprovalQueue.Action.FORCE_LOGIN,
                        3_000L, 60_000L).status());
        assertEquals(0, queue.size(3_000L));
    }

    @Test
    void expiredRequestCannotBeApproved() {
        AuthMeActionApprovalQueue queue = new AuthMeActionApprovalQueue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        queue.requestOrApprove(first, target, AuthMeActionApprovalQueue.Action.RECOVERY_TOKEN,
                1_000L, 30_000L);

        assertEquals(AuthMeActionApprovalQueue.Status.REQUESTED,
                queue.requestOrApprove(second, target, AuthMeActionApprovalQueue.Action.RECOVERY_TOKEN,
                        31_001L, 30_000L).status());
    }

    @Test
    void queueIsBounded() {
        AuthMeActionApprovalQueue queue = new AuthMeActionApprovalQueue(8);
        UUID actor = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            assertEquals(AuthMeActionApprovalQueue.Status.REQUESTED,
                    queue.requestOrApprove(actor, UUID.randomUUID(),
                            AuthMeActionApprovalQueue.Action.FORCE_LOGOUT, 1_000L, 60_000L).status());
        }
        assertEquals(AuthMeActionApprovalQueue.Status.FULL,
                queue.requestOrApprove(actor, UUID.randomUUID(),
                        AuthMeActionApprovalQueue.Action.FORCE_LOGOUT, 1_000L, 60_000L).status());
    }
}
