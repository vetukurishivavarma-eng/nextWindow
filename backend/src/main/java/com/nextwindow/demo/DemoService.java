package com.nextwindow.demo;

import com.nextwindow.common.ApiException;
import com.nextwindow.extraction.Availability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "nextwindow.demo", name = "enabled", havingValue = "true")
public class DemoService {
    public enum Action { PRICE_DROP, PRICE_INCREASE, RESTOCK, SELL_OUT, ADD_KEYWORD, REMOVE_KEYWORD, PAGE_CHANGE, FAIL_NEXT, RESET }

    private final DemoPageRepository pages;
    private final Clock clock;

    public DemoService(DemoPageRepository pages, Clock clock) {
        this.pages = pages;
        this.clock = clock;
    }

    @Transactional
    public Optional<DemoPage> ensure(String slug) {
        Optional<DemoPage> existing = pages.findById(slug);
        if (existing.isPresent()) return existing;
        DemoCatalog.Entry e = DemoCatalog.find(slug);
        if (e == null) return Optional.empty();
        return Optional.of(pages.saveAndFlush(e.toPage(clock.instant())));
    }

    @Transactional
    public List<DemoPage> all() {
        return DemoCatalog.ENTRIES.stream().map(e -> ensure(e.slug()).orElseThrow()).toList();
    }

    @Transactional
    public DemoPage simulate(String slug, Action action, String keyword) {
        DemoPage p = ensure(slug).orElseThrow(() -> ApiException.notFound("Demo source"));
        Instant now = clock.instant();
        switch (action) {
            case PRICE_DROP -> requirePrice(p).setPrice(p.getPrice().multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP));
            case PRICE_INCREASE -> requirePrice(p).setPrice(p.getPrice().multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP));
            case RESTOCK -> requirePrice(p).setAvailability(Availability.IN_STOCK);
            case SELL_OUT -> requirePrice(p).setAvailability(Availability.OUT_OF_STOCK);
            case ADD_KEYWORD -> {
                String k = keyword(keyword);
                if (!p.getBodyText().toLowerCase().contains(k.toLowerCase())) p.setBodyText(p.getBodyText() + "\nNew: " + k + ".");
            }
            case REMOVE_KEYWORD -> {
                String k = keyword(keyword);
                p.setBodyText(p.getBodyText().replaceAll("(?i)\\n?New: " + java.util.regex.Pattern.quote(k) + "\\.", "")
                        .replaceAll("(?i)" + java.util.regex.Pattern.quote(k), "").replaceAll(" {2,}", " "));
            }
            case PAGE_CHANGE -> {
                p.setRevision(p.getRevision() + 1);
                p.setBodyText(p.getBodyText() + "\nUpdate #" + p.getRevision() + ": schedule and details revised.");
            }
            case FAIL_NEXT -> p.setFailNext(p.getFailNext() + 1);
            case RESET -> {
                DemoCatalog.Entry e = DemoCatalog.find(slug);
                p.setTitle(e.title());
                p.setPrice(e.price());
                p.setCurrency(e.currency());
                p.setAvailability(e.availability());
                p.setBodyText(e.body());
                p.setFailNext(0);
                p.setRevision(0);
            }
        }
        p.setUpdatedAt(now);
        return p;
    }

    private static DemoPage requirePrice(DemoPage p) {
        if (p.getPrice() == null) throw ApiException.badRequest("NOT_A_PRODUCT", "This demo source has no price or stock.");
        return p;
    }

    private static String keyword(String k) {
        if (k == null || k.isBlank() || k.length() > 100) throw ApiException.badRequest("KEYWORD_REQUIRED", "Provide a keyword (max 100 characters).");
        return k.trim();
    }
}
