package com.nextwindow.watch;

public enum AlertMode {
    /** Alert once, then the watch completes and stops. */
    ONE_TIME,
    /** Alert every time the condition newly triggers, subject to the cooldown. */
    REPEAT
}
