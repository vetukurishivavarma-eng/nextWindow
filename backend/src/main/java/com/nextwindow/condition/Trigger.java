package com.nextwindow.condition;

/**
 * A condition that newly became true. {@code fingerprintBasis} identifies the specific event so identical
 * events are never alerted twice.
 */
public record Trigger(String alertType, String title, String message, String oldValue, String newValue, String fingerprintBasis) {}
