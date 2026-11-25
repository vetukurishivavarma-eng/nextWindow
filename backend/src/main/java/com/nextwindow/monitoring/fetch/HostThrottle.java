package com.nextwindow.monitoring.fetch;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Enforces a minimum delay between requests to the same host so no site receives bursts from us. */
@Component
public class HostThrottle {
    private final Map<String, Long> nextAllowed = new ConcurrentHashMap<>();
    private final Clock clock;

    public HostThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Reserves a request slot. Returns zero if the request may proceed now, otherwise the time to wait. */
    public Duration reserve(String host, Duration minDelay) {
        long now = clock.millis();
        long[] wait = {0};
        nextAllowed.compute(host, (h, next) -> {
            if (next == null || next <= now) {
                return now + minDelay.toMillis();
            }
            wait[0] = next - now;
            return next;
        });
        if (nextAllowed.size() > 10_000) {
            nextAllowed.entrySet().removeIf(e -> e.getValue() < now);
        }
        return Duration.ofMillis(wait[0]);
    }

    public void clear() {
        nextAllowed.clear();
    }
}
