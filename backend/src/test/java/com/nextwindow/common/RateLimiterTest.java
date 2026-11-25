package com.nextwindow.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    @Test
    void limitsPerKeyAndRefills() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        for (int i = 0; i < 5; i++) limiter.check(RateLimiter.Policy.REGISTER, "ip1");
        assertThatThrownBy(() -> limiter.check(RateLimiter.Policy.REGISTER, "ip1")).isInstanceOf(ApiException.class);
        assertThat(limiter.tryConsume(RateLimiter.Policy.REGISTER, "ip2")).isTrue();
        clock.now = clock.now.plus(Duration.ofMinutes(13));
        assertThat(limiter.tryConsume(RateLimiter.Policy.REGISTER, "ip1")).isTrue();
    }
}
