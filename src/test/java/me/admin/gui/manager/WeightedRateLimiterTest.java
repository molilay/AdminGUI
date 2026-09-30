package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightedRateLimiterTest {
    @Test
    void rejectedAttemptDoesNotConsumeAdditionalBudget() {
        WeightedRateLimiter limiter = new WeightedRateLimiter();
        UUID actor = UUID.randomUUID();
        assertEquals(WeightedRateLimiter.Outcome.ALLOWED,
                limiter.acquire(actor, 4, false, 1_000L, 60_000L, 5, 100, 20));
        assertEquals(WeightedRateLimiter.Outcome.ACTOR_LIMIT,
                limiter.acquire(actor, 2, false, 2_000L, 60_000L, 5, 100, 20));
        assertEquals(WeightedRateLimiter.Outcome.ALLOWED,
                limiter.acquire(actor, 1, false, 3_000L, 60_000L, 5, 100, 20));
    }

    @Test
    void bypassStillHasHardCap() {
        WeightedRateLimiter limiter = new WeightedRateLimiter();
        UUID actor = UUID.randomUUID();
        assertEquals(WeightedRateLimiter.Outcome.ALLOWED,
                limiter.acquire(actor, 5, true, 1_000L, 60_000L, 2, 100, 6));
        assertEquals(WeightedRateLimiter.Outcome.BYPASS_HARD_CAP,
                limiter.acquire(actor, 2, true, 2_000L, 60_000L, 2, 100, 6));
    }

    @Test
    void globalBurstProtectsAcrossActors() {
        WeightedRateLimiter limiter = new WeightedRateLimiter();
        assertEquals(WeightedRateLimiter.Outcome.ALLOWED,
                limiter.acquire(UUID.randomUUID(), 5, false, 1_000L, 60_000L, 10, 6, 20));
        assertEquals(WeightedRateLimiter.Outcome.GLOBAL_LIMIT,
                limiter.acquire(UUID.randomUUID(), 2, false, 2_000L, 60_000L, 10, 6, 20));
    }
}
