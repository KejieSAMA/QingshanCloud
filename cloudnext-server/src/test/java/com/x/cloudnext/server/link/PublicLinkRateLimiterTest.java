package com.x.cloudnext.server.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PublicLinkRateLimiter} (Requirement 8.4 / Design
 * §"Error Handling" 外链密码暴力破解保护).
 */
class PublicLinkRateLimiterTest {

    private static final Instant FIXED_NOW = Instant.parse("2025-01-01T12:00:00Z");

    @Test
    void admitsExactlyTenAttemptsPerMinute() {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        for (int i = 1; i <= PublicLinkRateLimiter.MAX_PER_MINUTE; i++) {
            assertTrue(limiter.tryAcquire("token-A", "1.2.3.4", FIXED_NOW),
                    "attempt " + i + " in the same minute must be admitted");
        }
        assertFalse(limiter.tryAcquire("token-A", "1.2.3.4", FIXED_NOW),
                "11th attempt in the same minute must be rejected");
    }

    @Test
    void resetsAfterWindowRollover() {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        for (int i = 0; i < PublicLinkRateLimiter.MAX_PER_MINUTE; i++) {
            limiter.tryAcquire("token-A", "1.2.3.4", FIXED_NOW);
        }
        assertFalse(limiter.tryAcquire("token-A", "1.2.3.4", FIXED_NOW));

        Instant nextMinute = FIXED_NOW.plusSeconds(60);
        assertTrue(limiter.tryAcquire("token-A", "1.2.3.4", nextMinute),
                "first attempt in the next minute must be admitted");
    }

    @Test
    void differentIpsHaveIndependentBudgets() {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        for (int i = 0; i < PublicLinkRateLimiter.MAX_PER_MINUTE; i++) {
            limiter.tryAcquire("token-A", "1.1.1.1", FIXED_NOW);
        }
        assertFalse(limiter.tryAcquire("token-A", "1.1.1.1", FIXED_NOW));
        assertTrue(limiter.tryAcquire("token-A", "2.2.2.2", FIXED_NOW),
                "another IP on the same token must not share the bucket");
    }

    @Test
    void differentTokensHaveIndependentBudgets() {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        for (int i = 0; i < PublicLinkRateLimiter.MAX_PER_MINUTE; i++) {
            limiter.tryAcquire("token-A", "1.1.1.1", FIXED_NOW);
        }
        assertFalse(limiter.tryAcquire("token-A", "1.1.1.1", FIXED_NOW));
        assertTrue(limiter.tryAcquire("token-B", "1.1.1.1", FIXED_NOW),
                "a different token must not share the bucket with the rejected one");
    }

    @Test
    void blankIpIsNormalizedToUnknown() {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        for (int i = 0; i < PublicLinkRateLimiter.MAX_PER_MINUTE; i++) {
            assertTrue(limiter.tryAcquire("token-A", null, FIXED_NOW));
        }
        // Blank string and null must collapse onto the same "unknown" bucket.
        assertFalse(limiter.tryAcquire("token-A", "  ", FIXED_NOW));
    }

    @Test
    void blankTokenIsRejectedAsIllegalArgument() {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
                () -> limiter.tryAcquire(null, "1.2.3.4", FIXED_NOW));
        assertThrows(IllegalArgumentException.class,
                () -> limiter.tryAcquire("  ", "1.2.3.4", FIXED_NOW));
    }

    @Test
    void cleanupRemovesIdleBuckets() {
        // Mutable clock: start at t0, advance later for cleanup.
        Instant[] cursor = { FIXED_NOW };
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return cursor[0];
            }
        };
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(clock);
        limiter.tryAcquire("token-A", "1.1.1.1", FIXED_NOW);
        assertEquals(1, limiter.size());

        // Move 6 minutes forward; the bucket window is now older than 5 min.
        cursor[0] = FIXED_NOW.plusSeconds(6 * 60L);
        limiter.cleanup();
        assertEquals(0, limiter.size(), "stale bucket must be evicted");
    }

    @Test
    void concurrentCallersOnSameKeyNeverExceedLimit() throws Exception {
        PublicLinkRateLimiter limiter = new PublicLinkRateLimiter(
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        int threads = 16;
        int attemptsPerThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger admitted = new AtomicInteger();
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire("token-A", "1.1.1.1", FIXED_NOW)) {
                            admitted.incrementAndGet();
                        }
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS),
                    "pool did not terminate in time");
        }
        assertEquals(PublicLinkRateLimiter.MAX_PER_MINUTE, admitted.get(),
                "exactly MAX_PER_MINUTE admissions across all concurrent callers");
    }
}
