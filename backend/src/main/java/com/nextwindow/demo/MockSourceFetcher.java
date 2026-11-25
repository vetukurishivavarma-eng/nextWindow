package com.nextwindow.demo;

import com.nextwindow.monitoring.fetch.FetchResult;
import com.nextwindow.monitoring.fetch.SourceFetcher;
import com.nextwindow.source.FetchMethod;
import com.nextwindow.source.MonitoringSource;
import org.jsoup.nodes.Entities;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Deterministic fetcher for demo:// sources. Renders an HTML page (with schema.org JSON-LD for products)
 * from the {@code demo_page} table so the full extraction and change-detection pipeline is exercised
 * without contacting external websites. Only active when demo mode is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "nextwindow.demo", name = "enabled", havingValue = "true")
public class MockSourceFetcher implements SourceFetcher {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final DemoService demo;
    private final DemoPageRepository pages;
    private final Clock clock;

    public MockSourceFetcher(DemoService demo, DemoPageRepository pages, Clock clock) {
        this.demo = demo;
        this.pages = pages;
        this.clock = clock;
    }

    @Override
    public boolean supports(FetchMethod method) {
        return method == FetchMethod.DEMO;
    }

    @Override
    public FetchResult fetch(MonitoringSource source) {
        return fetchUrl(source.getCanonicalUrl());
    }

    @Override
    public FetchResult fetchUrl(String canonicalUrl) {
        String slug = canonicalUrl.substring("demo://".length());
        DemoPage page = demo.ensure(slug).orElse(null);
        if (page == null) {
            return FetchResult.failure(FetchResult.Outcome.NOT_FOUND, 404, "Unknown demo source.");
        }
        if (pages.consumeFailure(slug) > 0) {
            return FetchResult.failure(FetchResult.Outcome.HTTP_ERROR, 503, "Simulated failure: the site returned HTTP 503.");
        }
        return FetchResult.success(200, render(page), "text/html; charset=utf-8", canonicalUrl);
    }

    String render(DemoPage p) {
        String title = Entities.escape(p.getTitle());
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
                .append(title).append(" | Demo Store</title>")
                .append("<meta property=\"og:title\" content=\"").append(title).append("\">")
                // A per-request id, as many sites emit; must not register as a change.
                .append("<meta name=\"request-id\" content=\"").append(java.util.UUID.randomUUID()).append("\">");
        if (p.getPrice() != null) {
            html.append("<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"name\":\"")
                    .append(json(p.getTitle())).append("\",\"offers\":{\"@type\":\"Offer\",\"price\":\"")
                    .append(p.getPrice().toPlainString()).append("\",\"priceCurrency\":\"").append(p.getCurrency())
                    .append("\",\"availability\":\"https://schema.org/").append(schemaAvailability(p)).append("\"}}</script>");
        }
        html.append("</head><body><header><nav>Demo Store · Home · Deals</nav></header><main><h1>").append(title).append("</h1>");
        if (p.getPrice() != null) {
            html.append("<p class=\"price\">").append(p.getCurrency()).append(' ').append(p.getPrice().toPlainString()).append("</p>")
                    .append("<p class=\"stock\">").append(p.getAvailability().isPurchasable() ? "In stock" : "Out of stock").append("</p>")
                    .append(p.getAvailability().isPurchasable() ? "<button>Add to cart</button>" : "<button disabled>Add to cart</button>");
        }
        for (String para : p.getBodyText().split("\n")) {
            html.append("<p>").append(Entities.escape(para)).append("</p>");
        }
        // Volatile timestamp, ignored by change detection.
        html.append("</main><footer>Page generated at ").append(TIME.format(clock.instant())).append(" UTC</footer></body></html>");
        return html.toString();
    }

    private static String schemaAvailability(DemoPage p) {
        return switch (p.getAvailability()) {
            case IN_STOCK -> "InStock";
            case LIMITED -> "LimitedAvailability";
            case PREORDER -> "PreOrder";
            case BACKORDER -> "BackOrder";
            case DISCONTINUED -> "Discontinued";
            default -> "OutOfStock";
        };
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c");
    }
}
