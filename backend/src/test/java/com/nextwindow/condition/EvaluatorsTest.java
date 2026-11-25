package com.nextwindow.condition;

import com.nextwindow.extraction.Availability;
import com.nextwindow.watch.WatchType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluatorsTest {
    private static Observation obs(String price, String cur, Availability a, String text, String hash) {
        return new Observation("Item", price == null ? null : new BigDecimal(price), cur, a, text, true, hash, null);
    }

    private static WatchCriteria c(WatchType t) {
        return new WatchCriteria(t, "Item", null, null);
    }

    @Test
    void priceDropTriggersOnlyOnDecrease() {
        var e = new Evaluators.PriceDrop();
        assertThat(e.evaluate(c(WatchType.PRICE_DROP), null, obs("100.00", "USD", Availability.UNKNOWN, "", "h")).triggered()).isFalse();
        var drop = e.evaluate(c(WatchType.PRICE_DROP), "100.00 USD", obs("90.00", "USD", Availability.UNKNOWN, "", "h"));
        assertThat(drop.triggered()).isTrue();
        assertThat(drop.trigger().message()).contains("$100.00").contains("$90.00").contains("10.0%");
        assertThat(drop.observed()).isEqualTo("90.00 USD");
        assertThat(e.evaluate(c(WatchType.PRICE_DROP), "90.00 USD", obs("95.00", "USD", Availability.UNKNOWN, "", "h")).triggered()).isFalse();
        assertThat(e.evaluate(c(WatchType.PRICE_DROP), "90.00 USD", obs("90.00", "USD", Availability.UNKNOWN, "", "h")).triggered()).isFalse();
    }

    @Test
    void priceDropIgnoresCurrencyChangeAndMissingPrice() {
        var e = new Evaluators.PriceDrop();
        var r = e.evaluate(c(WatchType.PRICE_DROP), "100.00 USD", obs("80.00", "EUR", Availability.UNKNOWN, "", "h"));
        assertThat(r.triggered()).isFalse();
        assertThat(r.observed()).isEqualTo("80.00 EUR");
        var missing = e.evaluate(c(WatchType.PRICE_DROP), "100.00 USD", obs(null, null, Availability.UNKNOWN, "", "h"));
        assertThat(missing.triggered()).isFalse();
        assertThat(missing.observed()).isNull(); // keep previous state
    }

    @Test
    void priceBelowTriggersOnCrossingTarget() {
        var e = new Evaluators.PriceBelow();
        var crit = new WatchCriteria(WatchType.PRICE_BELOW, "Item", new BigDecimal("50.00"), null);
        assertThat(e.evaluate(crit, null, obs("40.00", "USD", Availability.UNKNOWN, "", "h")).triggered()).isFalse(); // baseline
        assertThat(e.evaluate(crit, "NOT_MET:60.00", obs("55.00", "USD", Availability.UNKNOWN, "", "h")).triggered()).isFalse();
        var hit = e.evaluate(crit, "NOT_MET:55.00", obs("50.00", "USD", Availability.UNKNOWN, "", "h"));
        assertThat(hit.triggered()).isTrue();
        assertThat(hit.observed()).isEqualTo("MET:50.00");
        assertThat(e.evaluate(crit, "MET:50.00", obs("45.00", "USD", Availability.UNKNOWN, "", "h")).triggered()).isFalse();
    }

    @Test
    void stockAvailableRequiresGenuineTransition() {
        var e = new Evaluators.StockAvailable();
        var crit = c(WatchType.STOCK_AVAILABLE);
        assertThat(e.evaluate(crit, null, obs(null, null, Availability.IN_STOCK, "", "h")).triggered()).isFalse();
        assertThat(e.evaluate(crit, "OUT_OF_STOCK", obs(null, null, Availability.UNKNOWN, "", "h")).observed()).isNull();
        assertThat(e.evaluate(crit, "OUT_OF_STOCK", obs(null, null, Availability.IN_STOCK, "", "h")).triggered()).isTrue();
        assertThat(e.evaluate(crit, "PREORDER", obs(null, null, Availability.LIMITED, "", "h")).triggered()).isTrue();
        assertThat(e.evaluate(crit, "IN_STOCK", obs(null, null, Availability.IN_STOCK, "", "h")).triggered()).isFalse();
    }

    @Test
    void statusChangeBetweenKnownValues() {
        var e = new Evaluators.StatusChange();
        var crit = c(WatchType.STATUS_CHANGE);
        assertThat(e.evaluate(crit, "IN_STOCK", obs(null, null, Availability.OUT_OF_STOCK, "", "h")).trigger().message())
                .isEqualTo("Availability changed from In stock to Out of stock.");
        assertThat(e.evaluate(crit, "IN_STOCK", obs(null, null, Availability.UNKNOWN, "", "h")).triggered()).isFalse();
    }

    @Test
    void keywordFoundAndRemoved() {
        var found = new Evaluators.KeywordFound();
        var removed = new Evaluators.KeywordRemoved();
        var crit = new WatchCriteria(WatchType.KEYWORD_FOUND, "Jobs", null, "Java  Engineer");
        assertThat(found.evaluate(crit, null, obs(null, null, Availability.UNKNOWN, "We hire a java engineer", "h")).triggered()).isFalse();
        assertThat(found.evaluate(crit, "ABSENT", obs(null, null, Availability.UNKNOWN, "We hire a JAVA engineer", "h")).triggered()).isTrue();
        assertThat(found.evaluate(crit, "PRESENT", obs(null, null, Availability.UNKNOWN, "java engineer", "h")).triggered()).isFalse();
        assertThat(removed.evaluate(crit, "PRESENT", obs(null, null, Availability.UNKNOWN, "No roles", "h")).triggered()).isTrue();
        assertThat(removed.evaluate(crit, "ABSENT", obs(null, null, Availability.UNKNOWN, "No roles", "h")).triggered()).isFalse();
    }

    @Test
    void keywordAbsenceInTruncatedTextIsUnknown() {
        var crit = new WatchCriteria(WatchType.KEYWORD_REMOVED, "Jobs", null, "rust");
        var truncated = new Observation("t", null, null, Availability.UNKNOWN, "only part of page", false, "h", null);
        assertThat(new Evaluators.KeywordRemoved().evaluate(crit, "PRESENT", truncated).observed()).isNull();
    }

    @Test
    void anyChangeComparesHashesAndSummarizes() {
        var e = new Evaluators.AnyChange();
        var crit = c(WatchType.ANY_CHANGE);
        assertThat(e.evaluate(crit, null, obs(null, null, Availability.UNKNOWN, "a", "h1")).triggered()).isFalse();
        assertThat(e.evaluate(crit, "h1", obs(null, null, Availability.UNKNOWN, "a", "h1")).triggered()).isFalse();
        var changed = e.evaluate(crit, "h1", new Observation("t", null, null, Availability.UNKNOWN,
                "Trains run every 10 minutes", true, "h2", "Trains run every 5 minutes"));
        assertThat(changed.triggered()).isTrue();
        assertThat(changed.trigger().message()).isEqualTo("Changed “5” to “10”");
    }
}
