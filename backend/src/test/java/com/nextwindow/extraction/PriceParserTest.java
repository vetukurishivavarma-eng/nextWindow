package com.nextwindow.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceParserTest {
    @Test
    void parsesCommonFormats() {
        assertThat(PriceParser.parse("$1,299.99").get()).isEqualTo(new PriceParser.ParsedPrice(new BigDecimal("1299.99"), "USD"));
        assertThat(PriceParser.parse("1.299,00 €").get()).isEqualTo(new PriceParser.ParsedPrice(new BigDecimal("1299.00"), "EUR"));
        assertThat(PriceParser.parse("£49").get()).isEqualTo(new PriceParser.ParsedPrice(new BigDecimal("49.00"), "GBP"));
        assertThat(PriceParser.parse("CHF 12.5").get().amount()).isEqualByComparingTo("12.50");
        assertThat(PriceParser.parse("USD 1 234,50").get().amount()).isEqualByComparingTo("1234.50");
        assertThat(PriceParser.parse("₹ 2,499").get()).isEqualTo(new PriceParser.ParsedPrice(new BigDecimal("2499.00"), "INR"));
    }

    @Test
    void refusesAmbiguousOrInvalidText() {
        assertThat(PriceParser.parse("$10 - $20")).isEmpty();
        assertThat(PriceParser.parse("2 for $5")).isEmpty();
        assertThat(PriceParser.parse("Free")).isEmpty();
        assertThat(PriceParser.parse("$0.00")).isEmpty();
        assertThat(PriceParser.parse(null)).isEmpty();
    }

    @Test
    void parsesMachineAmounts() {
        assertThat(PriceParser.parseAmount("129.9")).contains(new BigDecimal("129.90"));
        assertThat(PriceParser.parseAmount("1e2")).contains(new BigDecimal("100.00"));
        assertThat(PriceParser.parseAmount("")).isEmpty();
        assertThat(PriceParser.parseAmount("-5")).isEmpty();
    }
}
