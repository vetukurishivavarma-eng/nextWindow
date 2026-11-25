package com.nextwindow.condition;

import com.nextwindow.extraction.Availability;
import com.nextwindow.extraction.TextNormalizer;
import com.nextwindow.watch.WatchType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/** Built-in condition evaluators. Each is a small, stateless, unit-tested component. */
public final class Evaluators {
    private Evaluators() {}

    static String encodePrice(BigDecimal price, String currency) {
        return price.toPlainString() + (currency == null ? "" : " " + currency);
    }

    record PriceState(BigDecimal price, String currency) {
        static PriceState decode(String v) {
            if (v == null) return null;
            String[] parts = v.split(" ");
            try {
                return new PriceState(new BigDecimal(parts[0]), parts.length > 1 ? parts[1] : null);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    @Component
    public static class PriceDrop implements ConditionEvaluator {
        public WatchType type() { return WatchType.PRICE_DROP; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            if (now.price() == null) return Evaluation.keep();
            String observed = encodePrice(now.price(), now.currency());
            PriceState prev = PriceState.decode(previous);
            if (prev == null) return Evaluation.state(observed);
            boolean comparable = prev.currency() == null || now.currency() == null || prev.currency().equals(now.currency());
            if (comparable && now.price().compareTo(prev.price()) < 0) {
                String cur = now.currency() != null ? now.currency() : prev.currency();
                String from = Formats.money(prev.price(), cur), to = Formats.money(now.price(), cur);
                return Evaluation.fire(observed, new Trigger("PRICE_DROP", "Price dropped: " + c.title(),
                        "Price fell from " + from + " to " + to + " (−" + Formats.percentDrop(prev.price(), now.price()) + ").",
                        from, to, "drop:" + prev.price().toPlainString() + "->" + now.price().toPlainString()));
            }
            return Evaluation.state(observed);
        }
    }

    @Component
    public static class PriceBelow implements ConditionEvaluator {
        public WatchType type() { return WatchType.PRICE_BELOW; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            if (now.price() == null || c.targetPrice() == null) return Evaluation.keep();
            boolean met = now.price().compareTo(c.targetPrice()) <= 0;
            String observed = (met ? "MET:" : "NOT_MET:") + now.price().toPlainString();
            if (previous == null) return Evaluation.state(observed);
            boolean wasMet = previous.startsWith("MET:");
            if (met && !wasMet) {
                String price = Formats.money(now.price(), now.currency());
                String target = Formats.money(c.targetPrice(), now.currency());
                String old = previous.contains(":") ? Formats.money(new BigDecimal(previous.substring(previous.indexOf(':') + 1)), now.currency()) : null;
                return Evaluation.fire(observed, new Trigger("PRICE_BELOW", "Target price reached: " + c.title(),
                        "Price is now " + price + ", at or below your target of " + target + ".", old, price,
                        "below:" + c.targetPrice().toPlainString() + ":" + now.price().toPlainString()));
            }
            return Evaluation.state(observed);
        }
    }

    @Component
    public static class StockAvailable implements ConditionEvaluator {
        public WatchType type() { return WatchType.STOCK_AVAILABLE; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            Availability a = now.availability();
            if (a == null || !a.isKnown()) return Evaluation.keep();
            if (previous == null) return Evaluation.state(a.name());
            Availability prev = parse(previous);
            if (a.isPurchasable() && prev.isKnown() && !prev.isPurchasable()) {
                return Evaluation.fire(a.name(), new Trigger("STOCK_AVAILABLE", "Back in stock: " + c.title(),
                        c.title() + " is now available (" + Formats.availability(a).toLowerCase() + "), previously "
                                + Formats.availability(prev).toLowerCase() + ".",
                        Formats.availability(prev), Formats.availability(a), "stock:" + prev.name() + "->" + a.name()));
            }
            return Evaluation.state(a.name());
        }
    }

    @Component
    public static class StatusChange implements ConditionEvaluator {
        public WatchType type() { return WatchType.STATUS_CHANGE; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            Availability a = now.availability();
            if (a == null || !a.isKnown()) return Evaluation.keep();
            if (previous == null) return Evaluation.state(a.name());
            Availability prev = parse(previous);
            if (prev.isKnown() && prev != a) {
                return Evaluation.fire(a.name(), new Trigger("STATUS_CHANGE", "Status changed: " + c.title(),
                        "Availability changed from " + Formats.availability(prev) + " to " + Formats.availability(a) + ".",
                        Formats.availability(prev), Formats.availability(a), "status:" + prev.name() + "->" + a.name()));
            }
            return Evaluation.state(a.name());
        }
    }

    abstract static class KeywordBase implements ConditionEvaluator {
        static final String PRESENT = "PRESENT", ABSENT = "ABSENT";

        String presence(WatchCriteria c, Observation now) {
            if (c.keyword() == null || c.keyword().isBlank() || now.text() == null) return null;
            boolean found = TextNormalizer.forMatch(now.text()).contains(TextNormalizer.forMatch(c.keyword()));
            if (!found && !now.textComplete()) return null; // can't be sure it's absent in truncated text
            return found ? PRESENT : ABSENT;
        }
    }

    @Component
    public static class KeywordFound extends KeywordBase {
        public WatchType type() { return WatchType.KEYWORD_FOUND; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            String p = presence(c, now);
            if (p == null) return Evaluation.keep();
            if (previous != null && p.equals(PRESENT) && previous.equals(ABSENT)) {
                return Evaluation.fire(p, new Trigger("KEYWORD_FOUND", "Keyword found: " + c.title(),
                        "“" + c.keyword() + "” now appears on the page.", "Not on page", "On page",
                        "kw-found:" + TextNormalizer.forMatch(c.keyword()) + ":" + Objects.toString(now.contentHash())));
            }
            return Evaluation.state(p);
        }
    }

    @Component
    public static class KeywordRemoved extends KeywordBase {
        public WatchType type() { return WatchType.KEYWORD_REMOVED; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            String p = presence(c, now);
            if (p == null) return Evaluation.keep();
            if (previous != null && p.equals(ABSENT) && previous.equals(PRESENT)) {
                return Evaluation.fire(p, new Trigger("KEYWORD_REMOVED", "Keyword removed: " + c.title(),
                        "“" + c.keyword() + "” no longer appears on the page.", "On page", "Not on page",
                        "kw-removed:" + TextNormalizer.forMatch(c.keyword()) + ":" + Objects.toString(now.contentHash())));
            }
            return Evaluation.state(p);
        }
    }

    @Component
    public static class AnyChange implements ConditionEvaluator {
        public WatchType type() { return WatchType.ANY_CHANGE; }

        public Evaluation evaluate(WatchCriteria c, String previous, Observation now) {
            if (now.contentHash() == null) return Evaluation.keep();
            if (previous == null || previous.equals(now.contentHash())) return Evaluation.state(now.contentHash());
            ChangeSummary.Summary s = ChangeSummary.summarize(now.previousText(), now.text());
            return Evaluation.fire(now.contentHash(), new Trigger("ANY_CHANGE", "Page changed: " + c.title(),
                    s.description(), s.removed(), s.added(), "change:" + now.contentHash()));
        }
    }

    static Availability parse(String v) {
        try {
            return Availability.valueOf(v);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Availability.UNKNOWN;
        }
    }
}
