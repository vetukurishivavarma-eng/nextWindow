package com.nextwindow.extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses human-formatted prices such as "$1,299.99", "1.299,00 €" or "USD 12". */
public final class PriceParser {
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:[.,\\s\\u00a0\\u202f']\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?");
    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            Map.entry("US$", "USD"), Map.entry("CA$", "CAD"), Map.entry("C$", "CAD"), Map.entry("A$", "AUD"),
            Map.entry("AU$", "AUD"), Map.entry("NZ$", "NZD"), Map.entry("HK$", "HKD"), Map.entry("S$", "SGD"),
            Map.entry("R$", "BRL"), Map.entry("€", "EUR"), Map.entry("£", "GBP"), Map.entry("¥", "JPY"),
            Map.entry("₹", "INR"), Map.entry("₩", "KRW"), Map.entry("₽", "RUB"), Map.entry("₺", "TRY"),
            Map.entry("zł", "PLN"), Map.entry("kr", "SEK"), Map.entry("CHF", "CHF"), Map.entry("$", "USD"));
    private static final Pattern ISO = Pattern.compile("\\b([A-Z]{3})\\b");
    private static final BigDecimal MAX = new BigDecimal("999999999999");

    public record ParsedPrice(BigDecimal amount, String currency) {}

    private PriceParser() {}

    /** Parses a machine-formatted amount (structured data). */
    public static Optional<BigDecimal> parseAmount(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim();
        if (v.isEmpty()) return Optional.empty();
        try {
            BigDecimal d = new BigDecimal(v);
            return valid(d);
        } catch (NumberFormatException e) {
            return parse(v).map(ParsedPrice::amount);
        }
    }

    /** Parses free text; requires a single number. */
    public static Optional<ParsedPrice> parse(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        if (t.isEmpty() || t.length() > 60) return Optional.empty();
        Matcher m = NUMBER.matcher(t);
        if (!m.find()) return Optional.empty();
        String num = m.group();
        if (m.find()) return Optional.empty(); // more than one number: ambiguous (e.g. ranges, "2 for $5")
        BigDecimal amount = toDecimal(num);
        if (amount == null) return Optional.empty();
        return valid(amount).map(a -> new ParsedPrice(a, detectCurrency(t)));
    }

    public static String detectCurrency(String text) {
        Matcher iso = ISO.matcher(text);
        while (iso.find()) {
            String code = iso.group(1);
            if (isCurrencyCode(code)) return code;
        }
        for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
            if (e.getKey().length() > 1 && text.contains(e.getKey())) return e.getValue();
        }
        for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
            if (e.getKey().length() == 1 && text.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    public static String normalizeCurrency(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (isCurrencyCode(v)) return v;
        String s = detectCurrency(raw.trim());
        return s;
    }

    private static boolean isCurrencyCode(String code) {
        try {
            java.util.Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static BigDecimal toDecimal(String num) {
        String n = num.replaceAll("[\\s\\u00a0\\u202f']", "");
        int lastDot = n.lastIndexOf('.');
        int lastComma = n.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            // Both present: the right-most one is the decimal separator.
            normalized = lastDot > lastComma ? n.replace(",", "") : n.replace(".", "").replace(',', '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char sep = lastDot >= 0 ? '.' : ',';
            int last = Math.max(lastDot, lastComma);
            boolean repeated = n.indexOf(sep) != last;
            boolean thousands = repeated || n.length() - last - 1 == 3;
            normalized = thousands ? n.replace(String.valueOf(sep), "") : n.replace(sep, '.');
        } else {
            normalized = n;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<BigDecimal> valid(BigDecimal d) {
        if (d.signum() <= 0 || d.compareTo(MAX) > 0) return Optional.empty();
        return Optional.of(d.setScale(2, RoundingMode.HALF_UP));
    }
}
