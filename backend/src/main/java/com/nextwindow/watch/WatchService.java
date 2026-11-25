package com.nextwindow.watch;

import com.nextwindow.common.ApiException;
import com.nextwindow.common.PageResponse;
import com.nextwindow.common.Pagination;
import com.nextwindow.condition.ConditionEngine;
import com.nextwindow.condition.Observation;
import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.history.CheckRun;
import com.nextwindow.history.CheckRunRepository;
import com.nextwindow.history.Snapshot;
import com.nextwindow.history.SnapshotRepository;
import com.nextwindow.monitoring.MonitoringService;
import com.nextwindow.notification.AlertRepository;
import com.nextwindow.source.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WatchService {
    public static final int MAX_WATCHES_PER_USER = 100;

    private final WatchRepository watches;
    private final MonitoringSourceRepository sources;
    private final SnapshotRepository snapshots;
    private final CheckRunRepository runs;
    private final AlertRepository alerts;
    private final SourceRegistry registry;
    private final UrlNormalizer normalizer;
    private final UrlSafetyValidator safety;
    private final ConditionEngine conditions;
    private final MonitoringService monitoring;
    private final NextWindowProperties props;
    private final Clock clock;

    public WatchService(WatchRepository watches, MonitoringSourceRepository sources, SnapshotRepository snapshots,
                        CheckRunRepository runs, AlertRepository alerts, SourceRegistry registry, UrlNormalizer normalizer,
                        UrlSafetyValidator safety, ConditionEngine conditions, MonitoringService monitoring,
                        NextWindowProperties props, Clock clock) {
        this.watches = watches;
        this.sources = sources;
        this.snapshots = snapshots;
        this.runs = runs;
        this.alerts = alerts;
        this.registry = registry;
        this.normalizer = normalizer;
        this.safety = safety;
        this.conditions = conditions;
        this.monitoring = monitoring;
        this.props = props;
        this.clock = clock;
    }

    /** Normalizes and validates a user-submitted URL, including SSRF checks. */
    public UrlNormalizer.NormalizedUrl validateUrl(String url) {
        UrlNormalizer.NormalizedUrl n = normalizer.normalize(url);
        if (n.isDemo()) {
            if (!props.demo().enabled()) throw new UrlRejectedException("Demo sources are not available on this server.");
            return n;
        }
        safety.validateResolvable(n.host(), n.port());
        return n;
    }

    @Transactional
    public WatchDtos.WatchResponse create(Long userId, WatchDtos.CreateWatchRequest req) {
        if (watches.countByUserId(userId) >= MAX_WATCHES_PER_USER) {
            throw ApiException.conflict("WATCH_LIMIT", "You have reached the limit of " + MAX_WATCHES_PER_USER + " watches.");
        }
        validateCriteria(req.watchType(), req.targetPrice(), req.targetKeyword());
        int interval = req.checkIntervalMinutes() == null ? props.monitoring().defaultIntervalMinutes() : req.checkIntervalMinutes();
        validateInterval(interval);
        UrlNormalizer.NormalizedUrl url = validateUrl(req.url());
        MonitoringSource source = registry.findOrCreate(url);

        Instant now = clock.instant();
        Optional<Snapshot> latest = snapshots.findFirstBySourceIdOrderByCreatedAtDescIdDesc(source.getId());
        String title = req.title() != null && !req.title().isBlank() ? req.title().trim()
                : latest.map(Snapshot::getExtractedTitle).filter(t -> !t.isBlank()).orElse(url.host());
        Watch w = new Watch(userId, source.getId(), clip(title, 200), req.watchType(), now);
        w.setTargetPrice(req.watchType().needsTargetPrice() ? req.targetPrice() : null);
        w.setTargetKeyword(req.watchType().needsKeyword() ? req.targetKeyword().trim() : null);
        w.setAlertMode(req.alertMode() == null ? AlertMode.REPEAT : req.alertMode());
        w.setCooldownMinutes(req.cooldownMinutes() == null ? 60 : req.cooldownMinutes());
        w.setCheckIntervalMinutes(interval);
        w.setExpiresAt(req.expiresAt());
        latest.ifPresent(s -> w.setLastObservedValue(baseline(w, s)));
        watches.save(w);
        registry.recomputeInterval(source.getId());
        return toResponse(w);
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchDtos.WatchResponse> list(Long userId, String filter, String query, int page, int size) {
        var pageable = Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Watch> p;
        if (query != null && !query.isBlank()) {
            p = watches.search(userId, "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "\\%") + "%", pageable);
        } else if ("active".equals(filter)) {
            p = watches.findByUserIdAndActive(userId, true, pageable);
        } else if ("paused".equals(filter)) {
            p = watches.findByUserIdAndActive(userId, false, pageable);
        } else {
            p = watches.findByUserId(userId, pageable);
        }
        Map<Long, MonitoringSource> srcs = sources.findAllById(p.getContent().stream().map(Watch::getSourceId).toList())
                .stream().collect(Collectors.toMap(MonitoringSource::getId, Function.identity()));
        Map<Long, Snapshot> latest = p.getContent().isEmpty() ? Map.of()
                : snapshots.findLatestForSources(srcs.keySet()).stream()
                .collect(Collectors.toMap(Snapshot::getSourceId, Function.identity()));
        return PageResponse.of(p, w -> WatchMapper.watch(w, srcs.get(w.getSourceId()), latest.get(w.getSourceId()), null,
                alerts.countByUserIdAndWatchIdAndReadFalse(userId, w.getId())));
    }

    @Transactional(readOnly = true)
    public WatchDtos.WatchResponse get(Long userId, Long id) {
        return toResponse(owned(userId, id));
    }

    @Transactional
    public WatchDtos.WatchResponse update(Long userId, Long id, WatchDtos.UpdateWatchRequest req) {
        Watch w = owned(userId, id);
        validateCriteria(req.watchType(), req.targetPrice(), req.targetKeyword());
        validateInterval(req.checkIntervalMinutes());
        if (req.expiresAt() != null && req.expiresAt().isBefore(clock.instant())) {
            throw ApiException.badRequest("INVALID_EXPIRY", "Expiry must be in the future.");
        }
        boolean criteriaChanged = w.getWatchType() != req.watchType()
                || !eq(w.getTargetPrice(), req.watchType().needsTargetPrice() ? req.targetPrice() : null)
                || !java.util.Objects.equals(w.getTargetKeyword(), req.watchType().needsKeyword() ? req.targetKeyword().trim() : null);
        w.setTitle(clip(req.title().trim(), 200));
        w.setWatchType(req.watchType());
        w.setTargetPrice(req.watchType().needsTargetPrice() ? req.targetPrice() : null);
        w.setTargetKeyword(req.watchType().needsKeyword() ? req.targetKeyword().trim() : null);
        w.setAlertMode(req.alertMode());
        w.setCooldownMinutes(req.cooldownMinutes());
        w.setCheckIntervalMinutes(req.checkIntervalMinutes());
        w.setExpiresAt(req.expiresAt());
        w.setUpdatedAt(clock.instant());
        if (criteriaChanged) {
            // New condition: establish a fresh baseline so the change itself never triggers an alert.
            w.setLastAlertFingerprint(null);
            w.setLastObservedValue(snapshots.findFirstBySourceIdOrderByCreatedAtDescIdDesc(w.getSourceId())
                    .map(s -> baseline(w, s)).orElse(null));
        }
        registry.recomputeInterval(w.getSourceId());
        return toResponse(w);
    }

    @Transactional
    public WatchDtos.WatchResponse setActive(Long userId, Long id, boolean active) {
        Watch w = owned(userId, id);
        Instant now = clock.instant();
        if (active) {
            if (w.getExpiresAt() != null && w.getExpiresAt().isBefore(now)) {
                throw ApiException.badRequest("WATCH_EXPIRED", "This watch has expired. Edit it to set a new expiry date first.");
            }
            w.setActive(true);
            w.setCompletedAt(null);
            if (w.getAlertMode() == AlertMode.ONE_TIME) w.setLastAlertFingerprint(null);
        } else {
            w.setActive(false);
        }
        w.setUpdatedAt(now);
        registry.recomputeInterval(w.getSourceId());
        return toResponse(w);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Watch w = owned(userId, id);
        Long sourceId = w.getSourceId();
        watches.delete(w);
        watches.flush();
        if (sources.countWatches(sourceId) == 0) {
            sources.deleteById(sourceId); // no one watches it any more: drop its history too
        } else {
            registry.recomputeInterval(sourceId);
        }
    }

    public WatchDtos.CheckNowResponse checkNow(Long userId, Long id) {
        Watch w = ownedReadOnly(userId, id);
        MonitoringSource src = sources.findById(w.getSourceId()).orElseThrow(() -> ApiException.notFound("Source"));
        Instant now = clock.instant();
        Duration minGap = src.getSourceType() == SourceType.DEMO ? Duration.ZERO : props.monitoring().manualCheckMinGap();
        if (src.getLastFetchedAt() != null && src.getLastFetchedAt().plus(minGap).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHECKED_RECENTLY",
                    "This page was checked moments ago. Please wait a couple of minutes before checking again.");
        }
        if (src.getStatus() == SourceStatus.BLOCKED && src.getNextFetchAt().isAfter(now) && src.getSourceType() != SourceType.DEMO) {
            throw ApiException.conflict("SOURCE_BLOCKED", "This site currently refuses automated access. We'll retry automatically.");
        }
        Optional<MonitoringService.CheckSummary> result = monitoring.checkNow(src.getId());
        WatchDtos.WatchResponse fresh = get(userId, id);
        return result.map(r -> new WatchDtos.CheckNowResponse(true, r.outcome().name(), r.alertsCreated(), r.message(), fresh))
                .orElseGet(() -> new WatchDtos.CheckNowResponse(false, null, 0, "A check is already in progress.", fresh));
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchDtos.SnapshotResponse> snapshots(Long userId, Long id, int page, int size) {
        Watch w = owned(userId, id);
        return PageResponse.of(snapshots.findBySourceIdOrderByCreatedAtDescIdDesc(w.getSourceId(), Pagination.of(page, size, Sort.unsorted())),
                WatchMapper::snapshot);
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchDtos.CheckRunResponse> checks(Long userId, Long id, int page, int size) {
        Watch w = owned(userId, id);
        MonitoringSource src = sources.findById(w.getSourceId()).orElseThrow();
        Page<CheckRun> p = runs.findBySourceIdOrderByStartedAtDesc(w.getSourceId(), Pagination.of(page, size, Sort.unsorted()));
        return PageResponse.of(p, c -> checkRun(c, src.getCanonicalUrl(), w));
    }

    public static WatchDtos.CheckRunResponse checkRun(CheckRun c, String url, Watch w) {
        return new WatchDtos.CheckRunResponse(c.getId(), c.getSourceId(), url, w == null ? null : w.getTitle(),
                w == null ? null : w.getId(), c.getStartedAt(), c.getFinishedAt(), c.getOutcome().name(), c.getHttpStatus(),
                c.getDurationMs(), c.getMessage(), c.getAlertsCreated());
    }

    private WatchDtos.WatchResponse toResponse(Watch w) {
        MonitoringSource src = sources.findById(w.getSourceId()).orElse(null);
        List<Snapshot> latest = snapshots.findTop2BySourceIdOrderByCreatedAtDescIdDesc(w.getSourceId());
        return WatchMapper.watch(w, src, latest.isEmpty() ? null : latest.get(0), latest.size() > 1 ? latest.get(1) : null,
                w.getId() == null ? 0 : alerts.countByUserIdAndWatchIdAndReadFalse(w.getUserId(), w.getId()));
    }

    private String baseline(Watch w, Snapshot s) {
        String text = s.getNormalizedText();
        boolean complete = text == null || text.length() < props.monitoring().maxStoredTextChars();
        Observation obs = new Observation(s.getExtractedTitle(), s.getExtractedPrice(), s.getCurrency(), s.getAvailability(),
                text, complete, s.getContentHash(), null);
        return conditions.baseline(w, obs);
    }

    private Watch owned(Long userId, Long id) {
        return watches.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Watch"));
    }

    private Watch ownedReadOnly(Long userId, Long id) {
        return owned(userId, id);
    }

    private static void validateCriteria(WatchType type, java.math.BigDecimal targetPrice, String keyword) {
        if (type.needsTargetPrice() && targetPrice == null) {
            throw ApiException.badRequest("TARGET_PRICE_REQUIRED", "Enter the target price for this watch.");
        }
        if (type.needsKeyword() && (keyword == null || keyword.isBlank())) {
            throw ApiException.badRequest("KEYWORD_REQUIRED", "Enter the keyword to look for.");
        }
        if (type.needsKeyword() && keyword.trim().length() < 2) {
            throw ApiException.badRequest("KEYWORD_TOO_SHORT", "Keywords must be at least 2 characters.");
        }
    }

    private void validateInterval(int minutes) {
        if (!WatchDtos.INTERVAL_OPTIONS.contains(minutes) || minutes < props.monitoring().minIntervalMinutes()) {
            throw ApiException.badRequest("INVALID_INTERVAL", "Choose a supported check frequency.");
        }
    }

    private static boolean eq(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
