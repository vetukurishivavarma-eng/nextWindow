package com.nextwindow.watch;

import com.nextwindow.condition.Formats;
import com.nextwindow.extraction.Availability;
import com.nextwindow.history.Snapshot;
import com.nextwindow.source.MonitoringSource;

import java.math.BigDecimal;

public final class WatchMapper {
    private WatchMapper() {}

    public static WatchDtos.SourceSummary source(MonitoringSource s) {
        if (s == null) return null;
        return new WatchDtos.SourceSummary(s.getId(), s.getCanonicalUrl(), s.getHost(), s.getSourceType().name(),
                s.getStatus().name(), s.getStatusMessage(), s.getLastFetchedAt(), s.getLastSuccessAt(), s.getNextFetchAt(),
                s.getFailureCount(), s.getCheckIntervalMinutes());
    }

    public static WatchDtos.StateResponse state(Snapshot s) {
        if (s == null) return null;
        return new WatchDtos.StateResponse(s.getExtractedTitle(), s.getExtractedPrice(), s.getCurrency(),
                s.getPriceSource() == null ? null : s.getPriceSource().name(), s.getAvailability(), s.getCreatedAt());
    }

    public static WatchDtos.SnapshotResponse snapshot(Snapshot s) {
        return new WatchDtos.SnapshotResponse(s.getId(), s.getExtractedTitle(), s.getExtractedPrice(), s.getCurrency(),
                s.getPriceSource() == null ? null : s.getPriceSource().name(), s.getAvailability(), s.getCreatedAt());
    }

    public static WatchDtos.WatchResponse watch(Watch w, MonitoringSource src, Snapshot current, Snapshot previous, long unread) {
        return new WatchDtos.WatchResponse(w.getId(), w.getTitle(), w.getWatchType(), w.getTargetPrice(), w.getTargetKeyword(),
                w.isActive(), w.getAlertMode(), w.getCooldownMinutes(), w.getCheckIntervalMinutes(), w.getExpiresAt(),
                w.getCompletedAt(), w.getCreatedAt(), w.getLastTriggeredAt(), w.getLastEvaluatedAt(),
                conditionState(w), conditionMet(w), unread, source(src), state(current), state(previous));
    }

    static boolean conditionMet(Watch w) {
        String v = w.getLastObservedValue();
        if (v == null) return false;
        return switch (w.getWatchType()) {
            case PRICE_BELOW -> v.startsWith("MET:");
            case KEYWORD_FOUND -> v.equals("PRESENT");
            case KEYWORD_REMOVED -> v.equals("ABSENT");
            case STOCK_AVAILABLE -> parse(v).isPurchasable();
            default -> false;
        };
    }

    /** Human-readable summary of the condition's current state. */
    static String conditionState(Watch w) {
        String v = w.getLastObservedValue();
        if (v == null) {
            return switch (w.getWatchType()) {
                case PRICE_DROP, PRICE_BELOW -> "Waiting for a readable price";
                case STOCK_AVAILABLE, STATUS_CHANGE -> "Waiting for readable availability";
                default -> "Waiting for the first check";
            };
        }
        return switch (w.getWatchType()) {
            case PRICE_DROP -> "Tracking from " + price(v);
            case PRICE_BELOW -> v.startsWith("MET:") ? "Target reached at " + Formats.money(new BigDecimal(v.substring(4)), null)
                    : "Above target (" + Formats.money(new BigDecimal(v.substring(v.indexOf(':') + 1)), null) + ")";
            case STOCK_AVAILABLE -> parse(v).isPurchasable() ? "Available now" : "Currently " + Formats.availability(parse(v)).toLowerCase();
            case STATUS_CHANGE -> "Currently " + Formats.availability(parse(v)).toLowerCase();
            case KEYWORD_FOUND, KEYWORD_REMOVED -> v.equals("PRESENT") ? "Keyword is on the page" : "Keyword is not on the page";
            case ANY_CHANGE -> "Watching for changes";
        };
    }

    private static String price(String v) {
        String[] parts = v.split(" ");
        try {
            return Formats.money(new BigDecimal(parts[0]), parts.length > 1 ? parts[1] : null);
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private static Availability parse(String v) {
        try {
            return Availability.valueOf(v);
        } catch (IllegalArgumentException e) {
            return Availability.UNKNOWN;
        }
    }
}
