package com.nextwindow.watch;

import com.nextwindow.extraction.Availability;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WatchDtos {
    private WatchDtos() {}

    public static final List<Integer> INTERVAL_OPTIONS = List.of(15, 30, 60, 180, 360, 720, 1440);

    public record CreateWatchRequest(
            @NotBlank(message = "Enter the URL to monitor") @Size(max = 2048) String url,
            @Size(max = 200, message = "Title must be at most 200 characters") String title,
            @NotNull(message = "Choose what to watch for") WatchType watchType,
            @DecimalMin(value = "0.01", message = "Target price must be positive") @Digits(integer = 12, fraction = 2) BigDecimal targetPrice,
            @Size(max = 200, message = "Keyword must be at most 200 characters") String targetKeyword,
            AlertMode alertMode,
            @Min(0) @Max(10080) Integer cooldownMinutes,
            Integer checkIntervalMinutes,
            @Future(message = "Expiry must be in the future") Instant expiresAt) {}

    public record UpdateWatchRequest(
            @NotBlank(message = "Title is required") @Size(max = 200) String title,
            @NotNull WatchType watchType,
            @DecimalMin(value = "0.01", message = "Target price must be positive") @Digits(integer = 12, fraction = 2) BigDecimal targetPrice,
            @Size(max = 200) String targetKeyword,
            @NotNull AlertMode alertMode,
            @NotNull @Min(0) @Max(10080) Integer cooldownMinutes,
            @NotNull Integer checkIntervalMinutes,
            Instant expiresAt) {}

    public record SourceSummary(Long id, String url, String host, String sourceType, String status, String statusMessage,
                                Instant lastFetchedAt, Instant lastSuccessAt, Instant nextFetchAt, int failureCount,
                                int checkIntervalMinutes) {}

    public record StateResponse(String title, BigDecimal price, String currency, String priceSource,
                                Availability availability, Instant capturedAt) {}

    public record WatchResponse(Long id, String title, WatchType watchType, BigDecimal targetPrice, String targetKeyword,
                                boolean active, AlertMode alertMode, int cooldownMinutes, int checkIntervalMinutes,
                                Instant expiresAt, Instant completedAt, Instant createdAt, Instant lastTriggeredAt,
                                Instant lastEvaluatedAt, String conditionState, boolean conditionMet, long unreadAlerts,
                                SourceSummary source, StateResponse current, StateResponse previous) {}

    public record SnapshotResponse(Long id, String title, BigDecimal price, String currency, String priceSource,
                                   Availability availability, Instant createdAt) {}

    public record CheckRunResponse(Long id, Long sourceId, String sourceUrl, String watchTitle, Long watchId,
                                   Instant startedAt, Instant finishedAt, String outcome, Integer httpStatus,
                                   long durationMs, String message, int alertsCreated) {}

    public record CheckNowResponse(boolean ran, String outcome, int alertsCreated, String message, WatchResponse watch) {}
}
