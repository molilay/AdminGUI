package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AuditIngressTest {

    @Test
    void fullQueueRejectsImmediatelyAndKeepsItsBound() {
        AuditIngress<Integer> ingress = new AuditIngress<>(2);
        assertTrue(ingress.offer(1));
        assertTrue(ingress.offer(2));
        assertTimeoutPreemptively(Duration.ofMillis(100), () -> assertFalse(ingress.offer(3)));
        assertEquals(2, ingress.size());
        assertEquals(2, ingress.capacity());
        assertEquals(1, ingress.rejected());
    }
}
