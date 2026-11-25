package com.nextwindow.source;

import com.nextwindow.config.NextWindowProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Finds or registers the shared MonitoringSource for a canonical URL. */
@Service
public class SourceRegistry {
    private final MonitoringSourceRepository sources;
    private final NextWindowProperties props;
    private final Clock clock;

    public SourceRegistry(MonitoringSourceRepository sources, NextWindowProperties props, Clock clock) {
        this.sources = sources;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonitoringSource findOrCreate(UrlNormalizer.NormalizedUrl url) {
        return sources.findByCanonicalUrl(url.canonical()).orElseGet(() -> {
            boolean demo = url.isDemo();
            int interval = demo ? props.monitoring().demoIntervalMinutes() : props.monitoring().defaultIntervalMinutes();
            Instant now = clock.instant();
            sources.insertIfAbsent(url.canonical(), url.host(),
                    (demo ? SourceType.DEMO : SourceType.WEB_PAGE).name(),
                    (demo ? FetchMethod.DEMO : FetchMethod.STATIC_HTTP).name(), interval, now);
            return sources.findByCanonicalUrl(url.canonical()).orElseThrow();
        });
    }

    /** Recomputes the shared check interval as the most frequent interval requested by any active watch. */
    @Transactional
    public void recomputeInterval(Long sourceId) {
        sources.findById(sourceId).ifPresent(s -> {
            Integer min = sources.minActiveWatchInterval(sourceId);
            int floor = s.getSourceType() == SourceType.DEMO
                    ? props.monitoring().demoIntervalMinutes() : props.monitoring().minIntervalMinutes();
            int interval = Math.max(floor, min == null ? props.monitoring().defaultIntervalMinutes() : min);
            if (s.getSourceType() == SourceType.DEMO) interval = props.monitoring().demoIntervalMinutes();
            if (interval != s.getCheckIntervalMinutes()) {
                s.setCheckIntervalMinutes(interval);
                Instant candidate = (s.getLastFetchedAt() == null ? clock.instant() : s.getLastFetchedAt()).plusSeconds(interval * 60L);
                if (candidate.isBefore(s.getNextFetchAt()) && s.getFailureCount() == 0) {
                    s.setNextFetchAt(candidate);
                }
                s.setUpdatedAt(clock.instant());
            }
        });
    }
}
