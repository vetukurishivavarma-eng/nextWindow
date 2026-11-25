package com.nextwindow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "nextwindow")
public record NextWindowProperties(
        @NotBlank @DefaultValue("http://localhost:5173") String publicUrl,
        @Valid @NotNull @DefaultValue Cors cors,
        @Valid @NotNull @DefaultValue Auth auth,
        @Valid @NotNull @DefaultValue Monitoring monitoring,
        @Valid @NotNull @DefaultValue Fetch fetch,
        @Valid @NotNull @DefaultValue Push push,
        @Valid @NotNull @DefaultValue Demo demo
) {
    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}

    public record Auth(
            @DefaultValue("nw_session") String cookieName,
            @DefaultValue("14d") Duration sessionTtl,
            @DefaultValue("false") boolean cookieSecure,
            @DefaultValue("Lax") String cookieSameSite,
            @DefaultValue("10") @Min(1) int maxSessionsPerUser) {}

    public record Monitoring(
            @DefaultValue("true") boolean schedulerEnabled,
            @DefaultValue("20") @Min(1) int batchSize,
            @DefaultValue("4") @Min(1) int workerThreads,
            @DefaultValue("5m") Duration lockDuration,
            @DefaultValue("60") @Min(1) int defaultIntervalMinutes,
            @DefaultValue("15") @Min(1) int minIntervalMinutes,
            @DefaultValue("1") @Min(1) int demoIntervalMinutes,
            @DefaultValue("24h") Duration maxBackoff,
            @DefaultValue("5") @Min(1) int degradedAfterFailures,
            @DefaultValue("15m") Duration analysisReuseWindow,
            @DefaultValue("2m") Duration manualCheckMinGap,
            @DefaultValue("300") @Min(10) int snapshotsPerSource,
            @DefaultValue("30") @Min(1) int checkRunRetentionDays,
            @DefaultValue("50000") @Min(1000) int maxStoredTextChars) {}

    public record Fetch(
            @DefaultValue("NextWindowBot/1.0 (+https://github.com/nextwindow; change monitoring)") String userAgent,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("10s") Duration readTimeout,
            @DefaultValue("2097152") @Min(1024) long maxBodyBytes,
            @DefaultValue("5") @Min(0) int maxRedirects,
            @DefaultValue("1") @Min(0) int maxRetries,
            @DefaultValue("10s") Duration hostMinDelay,
            @DefaultValue("true") boolean respectRobots,
            @DefaultValue("6h") Duration robotsCacheTtl) {}

    public record Push(
            @DefaultValue("") String vapidPublicKey,
            @DefaultValue("") String vapidPrivateKey,
            @DefaultValue("mailto:admin@example.com") String subject,
            @DefaultValue("86400") int ttlSeconds) {
        public boolean configured() {
            return vapidPublicKey != null && !vapidPublicKey.isBlank()
                    && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
        }
    }

    public record Demo(@DefaultValue("false") boolean enabled) {}
}
