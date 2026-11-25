package com.nextwindow.extraction;

import java.util.Locale;

/** Maps schema.org availability URLs and common metadata strings to {@link Availability}. */
public final class AvailabilityParser {
    private AvailabilityParser() {}

    public static Availability parse(String raw) {
        if (raw == null) return Availability.UNKNOWN;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        int slash = v.lastIndexOf('/');
        if (slash >= 0) v = v.substring(slash + 1);
        v = v.replaceAll("[\\s_-]", "");
        return switch (v) {
            case "instock", "available", "instoreonly", "onlineonly", "available for order", "availablefororder" -> Availability.IN_STOCK;
            case "limitedavailability" -> Availability.LIMITED;
            case "outofstock", "oos", "soldout", "unavailable" -> Availability.OUT_OF_STOCK;
            case "preorder", "presale" -> Availability.PREORDER;
            case "backorder" -> Availability.BACKORDER;
            case "discontinued" -> Availability.DISCONTINUED;
            default -> Availability.UNKNOWN;
        };
    }
}
