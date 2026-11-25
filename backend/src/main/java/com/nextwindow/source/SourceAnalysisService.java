package com.nextwindow.source;

import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.extraction.Availability;
import com.nextwindow.extraction.ExtractionResult;
import com.nextwindow.extraction.ExtractionService;
import com.nextwindow.history.Snapshot;
import com.nextwindow.history.SnapshotRepository;
import com.nextwindow.monitoring.fetch.FetchResult;
import com.nextwindow.monitoring.fetch.SourceFetcherRegistry;
import com.nextwindow.watch.WatchService;
import com.nextwindow.watch.WatchType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Previews what can be monitored on a URL before a watch is created. Reuses a recent shared snapshot when
 * one exists so popular pages are not fetched again for every user.
 */
@Service
public class SourceAnalysisService {

    public record TypeSupport(WatchType type, boolean recommended, boolean available, String note) {}

    public record AnalysisResponse(String canonicalUrl, boolean reachable, boolean fromSharedSource, String title,
                                   BigDecimal price, String currency, String priceSource, Availability availability,
                                   String availabilitySource, boolean productDetected, String description,
                                   String siteName, String textPreview, List<String> notes, List<TypeSupport> watchTypes,
                                   String problem, Instant analyzedAt) {}

    private final WatchService watchService;
    private final MonitoringSourceRepository sources;
    private final SnapshotRepository snapshots;
    private final SourceFetcherRegistry fetchers;
    private final ExtractionService extraction;
    private final NextWindowProperties props;
    private final Clock clock;

    public SourceAnalysisService(WatchService watchService, MonitoringSourceRepository sources, SnapshotRepository snapshots,
                                 SourceFetcherRegistry fetchers, ExtractionService extraction, NextWindowProperties props, Clock clock) {
        this.watchService = watchService;
        this.sources = sources;
        this.snapshots = snapshots;
        this.fetchers = fetchers;
        this.extraction = extraction;
        this.props = props;
        this.clock = clock;
    }

    public AnalysisResponse analyze(String rawUrl) {
        UrlNormalizer.NormalizedUrl url = watchService.validateUrl(rawUrl);
        Instant now = clock.instant();

        var existing = sources.findByCanonicalUrl(url.canonical());
        if (existing.isPresent() && existing.get().getLastSuccessAt() != null
                && existing.get().getLastSuccessAt().isAfter(now.minus(props.monitoring().analysisReuseWindow()))) {
            var snap = snapshots.findFirstBySourceIdOrderByCreatedAtDescIdDesc(existing.get().getId());
            if (snap.isPresent()) return fromSnapshot(url.canonical(), snap.get(), now);
        }

        FetchMethod method = url.isDemo() ? FetchMethod.DEMO : FetchMethod.STATIC_HTTP;
        if (!fetchers.isSupported(method)) {
            return failed(url.canonical(), "This kind of source is not available on this server.", false, now);
        }
        FetchResult result = fetchers.forMethod(method).fetchUrl(url.canonical());
        if (!result.isSuccess()) {
            boolean refused = result.outcome() == FetchResult.Outcome.BLOCKED || result.outcome() == FetchResult.Outcome.UNSAFE_URL
                    || result.outcome() == FetchResult.Outcome.UNSUPPORTED_CONTENT || result.outcome() == FetchResult.Outcome.TOO_LARGE;
            return failed(url.canonical(), result.message(), !refused, now);
        }
        ExtractionResult ex = extraction.extract(result.body(), result.contentType(), result.finalUrl());
        String preview = ex.normalizedText().length() > 280 ? ex.normalizedText().substring(0, 279) + "…" : ex.normalizedText();
        Map<String, String> meta = ex.metadata();
        return new AnalysisResponse(url.canonical(), true, false, ex.title(), ex.price(), ex.currency(),
                ex.priceSource() == null ? null : ex.priceSource().name(), ex.availability(),
                ex.availabilitySource() == null ? null : ex.availabilitySource().name(), ex.productDetected(),
                meta.get("description"), meta.get("siteName"), preview, ex.notes(),
                support(ex.price() != null, ex.availability().isKnown(), !ex.normalizedText().isBlank(), true), null, now);
    }

    private AnalysisResponse fromSnapshot(String url, Snapshot s, Instant now) {
        String text = s.getNormalizedText() == null ? "" : s.getNormalizedText();
        String preview = text.length() > 280 ? text.substring(0, 279) + "…" : text;
        List<String> notes = new ArrayList<>();
        if (s.getExtractedPrice() == null) notes.add("No product price was found. You can still monitor page changes and keywords.");
        if (!s.getAvailability().isKnown() && s.getExtractedPrice() != null) notes.add("Stock availability could not be determined reliably.");
        return new AnalysisResponse(url, true, true, s.getExtractedTitle(), s.getExtractedPrice(), s.getCurrency(),
                s.getPriceSource() == null ? null : s.getPriceSource().name(), s.getAvailability(), null,
                s.getExtractedPrice() != null, null, null, preview, notes,
                support(s.getExtractedPrice() != null, s.getAvailability().isKnown(), !text.isBlank(), true), null, now);
    }

    private AnalysisResponse failed(String url, String problem, boolean allowManual, Instant now) {
        List<String> notes = new ArrayList<>();
        if (allowManual) {
            notes.add("We couldn't read the page right now. You can still create a watch; NextWindow will keep trying "
                    + "and start comparing once a check succeeds.");
        }
        List<TypeSupport> types = allowManual ? support(false, false, true, false) : List.of();
        return new AnalysisResponse(url, false, false, null, null, null, null, Availability.UNKNOWN, null, false,
                null, null, null, notes, types, problem, now);
    }

    static List<TypeSupport> support(boolean price, boolean availability, boolean text, boolean reachable) {
        String noPrice = reachable ? "No reliable price detected on this page. The watch will start once a price is readable."
                : "Price unknown until the page can be read.";
        String noStock = reachable ? "Stock status isn't published in a machine-readable way on this page."
                : "Availability unknown until the page can be read.";
        return List.of(
                new TypeSupport(WatchType.PRICE_DROP, price, true, price ? null : noPrice),
                new TypeSupport(WatchType.PRICE_BELOW, price, true, price ? null : noPrice),
                new TypeSupport(WatchType.STOCK_AVAILABLE, availability, true, availability ? null : noStock),
                new TypeSupport(WatchType.STATUS_CHANGE, availability, true, availability ? null : noStock),
                new TypeSupport(WatchType.KEYWORD_FOUND, text && !price, true, null),
                new TypeSupport(WatchType.KEYWORD_REMOVED, false, true, null),
                new TypeSupport(WatchType.ANY_CHANGE, text && !price, true, text ? null : "This page has little readable text."));
    }
}
