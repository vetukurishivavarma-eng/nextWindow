package com.nextwindow.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class PushDtos {
    private PushDtos() {}

    public record Keys(@NotBlank @Size(max = 255) String p256dh, @NotBlank @Size(max = 255) String auth) {}

    public record SubscribeRequest(@NotBlank @Size(max = 2048) String endpoint, @Valid @NotNull Keys keys) {}

    public record UnsubscribeRequest(@NotBlank @Size(max = 2048) String endpoint) {}

    public record SubscriptionResponse(Long id, String device, String endpointHost, Instant createdAt, Instant lastSuccessAt, int failureCount) {}

    public record TestResult(int devices, int delivered, int failed, int removed, String message) {}
}
