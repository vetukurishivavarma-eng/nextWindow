package com.nextwindow.history;

public enum CheckOutcome {
    /** First successful check; baseline stored, no alerts. */
    BASELINE,
    CHANGED,
    UNCHANGED,
    FAILED,
    BLOCKED,
    RATE_LIMITED
}
