package com.nextwindow.condition;

import com.nextwindow.extraction.Availability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public final class Formats {
    private Formats() {}

    public static String money(BigDecimal amount, String currency) {
        if (amount == null) return "unknown";
        String n = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        if (currency == null) return n;
        try {
            String symbol = Currency.getInstance(currency).getSymbol(Locale.US);
            return symbol.length() == 1 ? symbol + n : n + " " + currency;
        } catch (IllegalArgumentException e) {
            return n + " " + currency;
        }
    }

    public static String availability(Availability a) {
        return switch (a) {
            case IN_STOCK -> "In stock";
            case OUT_OF_STOCK -> "Out of stock";
            case PREORDER -> "Pre-order";
            case BACKORDER -> "Backorder";
            case LIMITED -> "Limited availability";
            case DISCONTINUED -> "Discontinued";
            case UNKNOWN -> "Unknown";
        };
    }

    public static String percentDrop(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.signum() == 0) return "";
        BigDecimal pct = from.subtract(to).multiply(BigDecimal.valueOf(100)).divide(from, 1, RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }
}
