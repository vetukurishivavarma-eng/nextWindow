package com.nextwindow.demo;

import com.nextwindow.extraction.Availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Default state of the deterministic demo sources (demo://slug). */
public final class DemoCatalog {
    private DemoCatalog() {}

    public record Entry(String slug, String title, BigDecimal price, String currency, Availability availability,
                        String body, String description) {
        DemoPage toPage(Instant now) {
            return new DemoPage(slug, title, price, currency, availability, body, now);
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("headphones", "Aurora Wireless Headphones", new BigDecimal("199.00"), "USD", Availability.IN_STOCK,
                    "Immersive noise cancelling headphones with 40 hours of battery life. Free shipping on orders over $50.",
                    "Product in stock — try a price drop"),
            new Entry("sneakers", "Trailblazer Running Shoes", new BigDecimal("129.99"), "USD", Availability.OUT_OF_STOCK,
                    "Lightweight trail running shoes with a grippy outsole. Size 10 is currently out of stock.",
                    "Out of stock — try a restock"),
            new Entry("console", "Nimbus Game Console", new BigDecimal("499.00"), "EUR", Availability.OUT_OF_STOCK,
                    "Next generation console with 2TB storage. Launch bundle includes two controllers.",
                    "Euro-priced product, out of stock"),
            new Entry("careers", "Careers at Lumen Labs", null, null, Availability.UNKNOWN,
                    "Open roles: Product Designer (Remote). Data Analyst (Berlin). We hire across Europe and North America.",
                    "No price — try keyword watches"),
            new Entry("transit", "City Transit Service Updates", null, null, Availability.UNKNOWN,
                    "All lines are running normally. No planned engineering works this weekend.",
                    "Text page — try any-change watches"));

    public static Entry find(String slug) {
        return ENTRIES.stream().filter(e -> e.slug().equals(slug)).findFirst().orElse(null);
    }
}
