package com.nextwindow.notification;

public enum PushStatus {
    PENDING,
    /** Server has no VAPID keys configured; in-app only. */
    NOT_CONFIGURED,
    /** User turned push off. */
    DISABLED,
    NO_SUBSCRIPTIONS,
    /** Accepted by the push service for every device. */
    SENT,
    /** Accepted for some devices only. */
    PARTIAL,
    FAILED
}
