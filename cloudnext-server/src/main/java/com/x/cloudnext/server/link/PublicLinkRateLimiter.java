package com.x.cloudnext.server.link;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * In-memory fixed-window rate limiter for the public-link verify endpoint
 * (Requirement 8.4 / Design §"Error Handling" 外链密码暴力破解保护).
 *
 * <p>Caps each {@code (token, ip)} pair at {@link #MAX_PER_MINUTE} verify
 * attempts per UTC wall-clock minute. The 11th attempt in the same minute is
 * rejected; the controller is expected to translate {@code false} returns into
 * HTTP&nbsp;429 with {@link #EXCEEDED_MESSAGE} as the response message.
 *
 * <p>Phase-1 implementation only: a {@link ConcurrentHashMap} of fixed-window
 * buckets, lock-free updates via {@link AtomicReference}, and a periodic
 * cleanup task that drops buckets last touched more than five minutes ago.
 * Redis is intentionally out of scope at this stage.
 *
 * <p>This component is wired into the public-link verify controller in a later
 * task; it does not register any HTTP endpoints itself.
 */
@Component
public class PublicLinkRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicLinkRateLimiter.class);

    /** Hard cap per (token, ip) per minute. */
    public static final int MAX_PER_MINUTE = 10;

    /** Stable error message the controller surfaces with the 429 response. */
    public static final String EXCEEDED_MESSAGE = "link verify rate limited";

    /** Buckets whose window started more than this many seconds ago are pruned. */
    private static final long IDLE_TTL_SECONDS = 300L;

    private static final String UNKNOWN_IP = "unknown";

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public PublicLinkRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Test-only constructor: injectable {@link Clock}. */
    PublicLinkRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns {@code true} if the call is admitted, {@code false} if it should
     * be rejected with HTTP&nbsp;429.
     *
     * @param token public link token (immutable identifier of the share);
     *              must not be null or blank
     * @param ip    client IP from the controller (X-Forwarded-For-aware
     *              resolution is the controller's job; this method just hashes
     *              the supplied string). Null/blank is normalized to
     *              {@code "unknown"} so unit tests can omit it.
     * @param now   current instant; injected for testability
     * @throws IllegalArgumentException if {@code token} is null/blank or
     *                                  {@code now} is null
     */
    public boolean tryAcquire(String token, String ip, Instant now) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        String normalizedIp = StringUtils.hasText(ip) ? ip : UNKNOWN_IP;
        String key = token + "|" + normalizedIp;
        long currentWindowStart = (now.getEpochSecond() / 60L) * 60L;

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(currentWindowStart));
        return bucket.increment(currentWindowStart);
    }

    /** Convenience overload using {@link Instant#now(Clock)} with the bean's {@link Clock}. */
    public boolean tryAcquire(String token, String ip) {
        return tryAcquire(token, ip, Instant.now(clock));
    }

    /**
     * Periodic cleanup. Walks the map and removes buckets whose window started
     * more than {@link #IDLE_TTL_SECONDS} seconds ago. Reuses the application's
     * existing {@code @EnableScheduling} on {@code CloudNextServerApplication}.
     */
    @Scheduled(fixedDelay = 60_000L)
    void cleanup() {
        long cutoff = Instant.now(clock).getEpochSecond() - IDLE_TTL_SECONDS;
        int removed = 0;
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> e = it.next();
            if (e.getValue().windowStart() < cutoff) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("PublicLinkRateLimiter cleanup removed {} idle buckets, {} remaining",
                    removed, buckets.size());
        }
    }

    /** Package-private size accessor for tests. */
    int size() {
        return buckets.size();
    }

    /**
     * Lock-free fixed-window bucket. Each state snapshot is an immutable
     * {@code long[2]} of {@code [windowStartEpochSecond, count]}; updates use
     * CAS so concurrent callers within the same window cannot lose increments
     * and rollovers cannot accidentally double-reset.
     */
    private static final class Bucket {

        private final AtomicReference<long[]> state;

        Bucket(long windowStart) {
            this.state = new AtomicReference<>(new long[] { windowStart, 0L });
        }

        long windowStart() {
            return state.get()[0];
        }

        boolean increment(long currentWindowStart) {
            while (true) {
                long[] current = state.get();
                long[] next;
                if (current[0] != currentWindowStart) {
                    // Window rolled over — first attempt of the new minute.
                    next = new long[] { currentWindowStart, 1L };
                } else {
                    long incremented = current[1] + 1L;
                    if (incremented > MAX_PER_MINUTE) {
                        // Window already full; reject without bumping the count
                        // so a sustained burst can't keep extending the bucket.
                        return false;
                    }
                    next = new long[] { currentWindowStart, incremented };
                }
                if (state.compareAndSet(current, next)) {
                    return true;
                }
                // Lost the race; retry with the latest snapshot.
            }
        }
    }
}
