package com.nextwindow.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter. Suitable for a single-instance deployment;
 * swap for a shared store if the API is scaled horizontally.
 */
@Component
public class RateLimiter {

    public enum Policy {
        LOGIN(10, Duration.ofMinutes(5)),
        REGISTER(5, Duration.ofHours(1)),
        WATCH_CREATE(30, Duration.ofHours(1)),
        SOURCE_ANALYZE(20, Duration.ofMinutes(10)),
        MANUAL_CHECK(20, Duration.ofMinutes(10)),
        PUSH_TEST(5, Duration.ofMinutes(10)),
        SENSITIVE(10, Duration.ofMinutes(10)),
        DEMO(120, Duration.ofMinutes(10));

        final int capacity;
        final Duration window;

        Policy(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }
    }

    private static final int MAX_BUCKETS = 50_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Consumes one token or throws a 429 {@link ApiException}. */
    public void check(Policy policy, String key) {
        if (!tryConsume(policy, key)) {
            throw ApiException.tooManyRequests("Too many requests. Please wait a little and try again.");
        }
    }

    public boolean tryConsume(Policy policy, String key) {
        if (buckets.size() > MAX_BUCKETS) {
            evictFull();
        }
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(policy.name() + ':' + key, k -> new Bucket(policy.capacity, now));
        return bucket.tryConsume(policy, now);
    }

    public void reset() {
        buckets.clear();
    }

    private void evictFull() {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> e.getValue().isIdle(now));
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        synchronized boolean tryConsume(Policy p, long now) {
            double refillPerMs = (double) p.capacity / p.window.toMillis();
            tokens = Math.min(p.capacity, tokens + (now - lastRefill) * refillPerMs);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized boolean isIdle(long now) {
            return now - lastRefill > Duration.ofHours(2).toMillis();
        }
    }
}
