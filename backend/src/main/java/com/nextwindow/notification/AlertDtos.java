package com.nextwindow.notification;

import java.time.Instant;

public final class AlertDtos {
    private AlertDtos() {}

    public record AlertResponse(Long id, Long watchId, String watchTitle, String sourceUrl, String alertType, String title,
                                String message, String oldValue, String newValue, boolean read, String pushStatus,
                                Instant createdAt) {}

    public record UnreadCount(long unread) {}
}
