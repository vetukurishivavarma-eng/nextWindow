package com.nextwindow.monitoring;

import com.nextwindow.common.Hashing;
import com.nextwindow.condition.ConditionEngine;
import com.nextwindow.condition.Evaluation;
import com.nextwindow.condition.Observation;
import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.extraction.ExtractionResult;
import com.nextwindow.extraction.ExtractionService;
import com.nextwindow.history.CheckOutcome;
import com.nextwindow.history.CheckRun;
import com.nextwindow.history.CheckRunRepository;
import com.nextwindow.history.Snapshot;
import com.nextwindow.history.SnapshotRepository;
import com.nextwindow.monitoring.fetch.FetchResult;
import com.nextwindow.monitoring.fetch.SourceFetcherRegistry;
import com.nextwindow.notification.AlertService;
import com.nextwindow.source.MonitoringSource;
import com.nextwindow.source.MonitoringSourceRepository;
import com.nextwindow.source.SourceStatus;
import com.nextwindow.watch.Watch;
import com.nextwindow.watch.WatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The monitoring pipeline for one shared source: fetch once, extract, compare with the previous snapshot,
 * evaluate every active watch on the source, raise deduplicated alerts, and reschedule.
 * Network I/O happens outside database transactions; all state changes of a check commit atomically.
 */
@Service
public class MonitoringService {
    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    public record CheckSummary(Long sourceId, CheckOutcome outcome, int alertsCreated, String message) {}

    private final MonitoringSourceRepository sources;
    private final SnapshotRepository snapshots;
    private final CheckRunRepository runs;
    private final WatchRepository watches;
    private final SourceFetcherRegistry fetchers;
    private final ExtractionService extraction;
    private final ConditionEngine conditions;
    private final AlertService alertService;
    private final TransactionTemplate tx;
    private final NextWindowProperties.Monitoring props;
    private final Clock clock;
    private final WorkerIdentity worker;

    public MonitoringService(MonitoringSourceRepository sources, SnapshotRepository snapshots, CheckRunRepository runs,
                             WatchRepository watches, SourceFetcherRegistry fetchers, ExtractionService extraction,
                             ConditionEngine conditions, AlertService alertService, PlatformTransactionManager txm,
                             NextWindowProperties properties, Clock clock, WorkerIdentity worker) {
        this.sources = sources;
        this.snapshots = snapshots;
        this.runs = runs;
        this.watches = watches;
        this.fetchers = fetchers;
        this.extraction = extraction;
        this.conditions = conditions;
        this.alertService = alertService;
        this.tx = new TransactionTemplate(txm);
        this.props = properties.monitoring();
        this.clock = clock;
        this.worker = worker;
    }

    public String workerId() {
        return worker.id();
    }

    /** Claims up to {@code limit} due sources for this worker. */
    public List<Long> claimDue(int limit) {
        Instant now = clock.instant();
        return sources.claimDue(worker.id(), now, now.plus(props.lockDuration()), limit);
    }

    /** Checks a source immediately if no other worker holds it. Empty when it is already being checked. */
    public Optional<CheckSummary> checkNow(Long sourceId) {
        Instant now = clock.instant();
        if (sources.tryLock(sourceId, worker.id(), now, now.plus(props.lockDuration())) == 0) {
            return Optional.empty();
        }
        return Optional.of(runLeased(sourceId));
    }

    /** Runs a check for a source whose lease this worker holds, always releasing the lease afterwards. */
    public CheckSummary runLeased(Long sourceId) {
        try {
            return check(sourceId);
        } catch (RuntimeException e) {
            log.error("Monitoring check for source {} failed unexpectedly", sourceId, e);
            return new CheckSummary(sourceId, CheckOutcome.FAILED, 0, "Internal error");
        } finally {
            sources.release(sourceId, worker.id());
        }
    }

    private CheckSummary check(Long sourceId) {
        MonitoringSource source = sources.findById(sourceId).orElse(null);
        if (source == null) return new CheckSummary(sourceId, CheckOutcome.FAILED, 0, "Source no longer exists");
        Instant started = clock.instant();

        FetchResult result;
        if (!fetchers.isSupported(source.getFetchMethod())) {
            result = FetchResult.failure(FetchResult.Outcome.UNSUPPORTED_CONTENT, null,
                    "This source type is disabled on this server.");
        } else {
            try {
                result = fetchers.forMethod(source.getFetchMethod()).fetch(source);
            } catch (RuntimeException e) {
                log.warn("Fetcher error for source {}: {}", sourceId, e.toString());
                result = FetchResult.failure(FetchResult.Outcome.NETWORK_ERROR, null, "Unexpected error while fetching the page.");
            }
        }

        if (result.isSuccess()) {
            ExtractionResult extracted;
            try {
                extracted = extraction.extract(result.body(), result.contentType(), result.finalUrl());
            } catch (RuntimeException e) {
                log.warn("Extraction error for source {}: {}", sourceId, e.toString());
                FetchResult failed = FetchResult.failure(FetchResult.Outcome.UNSUPPORTED_CONTENT, result.httpStatus(),
                        "The page could not be read.");
                return tx.execute(s -> recordFailure(sourceId, started, failed));
            }
            final FetchResult ok = result;
            return tx.execute(s -> recordSuccess(sourceId, started, ok, extracted));
        }
        final FetchResult failed = result;
        return tx.execute(s -> recordFailure(sourceId, started, failed));
    }

    public static String contentHash(ExtractionResult e) {
        return Hashing.sha256Hex(String.join("|",
                Objects.toString(e.title(), ""),
                e.price() == null ? "" : e.price().toPlainString(),
                Objects.toString(e.currency(), ""),
                e.availability().name(),
                e.textHash()));
    }

    private CheckSummary recordSuccess(Long sourceId, Instant started, FetchResult result, ExtractionResult ex) {
        MonitoringSource src = sources.findById(sourceId).orElseThrow();
        Instant now = clock.instant();
        String hash = contentHash(ex);
        Optional<Snapshot> prev = snapshots.findFirstBySourceIdOrderByCreatedAtDescIdDesc(sourceId);
        boolean baseline = prev.isEmpty();
        boolean changed = !baseline && !prev.get().getContentHash().equals(hash);

        Snapshot snap = null;
        if (baseline || changed) {
            String text = ex.normalizedText();
            if (text.length() > props.maxStoredTextChars()) text = text.substring(0, props.maxStoredTextChars());
            snap = snapshots.save(new Snapshot(sourceId, hash, ex.textHash(), ex.title(), ex.price(), ex.currency(),
                    ex.priceSource(), ex.availability(), text, now));
        }

        Observation obs = new Observation(ex.title(), ex.price(), ex.currency(), ex.availability(), ex.normalizedText(),
                true, hash, prev.map(Snapshot::getNormalizedText).orElse(null));
        int created = 0;
        for (Watch w : watches.findBySourceIdAndActiveTrueOrderByIdAsc(sourceId)) {
            if (w.getExpiresAt() != null && w.getExpiresAt().isBefore(now)) {
                w.setActive(false);
                w.setCompletedAt(now);
                continue;
            }
            if (ex.title() != null && w.getTitle().equals(src.getHost())) {
                // Watch was created before the page was readable and got a placeholder title.
                w.setTitle(ex.title().length() > 200 ? ex.title().substring(0, 200) : ex.title());
            }
            Evaluation ev = conditions.evaluate(w, obs);
            boolean deferred = false;
            if (ev.triggered()) {
                AlertService.Result r = alertService.raise(w, ev.trigger());
                if (r.outcome() == AlertService.Outcome.CREATED) created++;
                // During cooldown keep the old state so the event is re-evaluated (and alerted) once it ends.
                deferred = r.outcome() == AlertService.Outcome.COOLDOWN;
            }
            if (!deferred && ev.observed() != null) w.setLastObservedValue(ev.observed());
            w.setLastEvaluatedAt(now);
        }

        src.setStatus(SourceStatus.ACTIVE);
        src.setStatusMessage(null);
        src.setFailureCount(0);
        src.setLastFetchedAt(now);
        src.setLastSuccessAt(now);
        src.setLastContentHash(hash);
        src.setNextFetchAt(now.plus(withJitter(Duration.ofMinutes(src.getCheckIntervalMinutes()))));
        src.setUpdatedAt(now);

        CheckOutcome outcome = baseline ? CheckOutcome.BASELINE : changed ? CheckOutcome.CHANGED : CheckOutcome.UNCHANGED;
        String message = switch (outcome) {
            case BASELINE -> "Baseline captured. Future checks compare against it.";
            case CHANGED -> "Change detected" + (created > 0 ? "; " + created + (created == 1 ? " alert" : " alerts") + " sent." : ".");
            default -> "No meaningful change.";
        };
        runs.save(new CheckRun(sourceId, started, now, outcome, result.httpStatus(), message,
                snap == null ? null : snap.getId(), created));
        return new CheckSummary(sourceId, outcome, created, message);
    }

    private CheckSummary recordFailure(Long sourceId, Instant started, FetchResult result) {
        MonitoringSource src = sources.findById(sourceId).orElseThrow();
        Instant now = clock.instant();
        Duration interval = Duration.ofMinutes(src.getCheckIntervalMinutes());

        // Our own politeness throttle deferred the request: not a failure, just reschedule.
        if (result.outcome() == FetchResult.Outcome.RATE_LIMITED && result.httpStatus() == null) {
            Duration wait = result.retryAfter() == null ? Duration.ofMinutes(1) : result.retryAfter();
            src.setNextFetchAt(now.plus(wait).plusSeconds(1));
            src.setUpdatedAt(now);
            return new CheckSummary(sourceId, CheckOutcome.RATE_LIMITED, 0, result.message());
        }

        SourceStatus before = src.getStatus();
        int failures = src.getFailureCount() + 1;
        src.setFailureCount(failures);
        src.setLastFetchedAt(now);
        src.setStatusMessage(result.message());
        src.setUpdatedAt(now);

        CheckOutcome outcome;
        Duration next;
        switch (result.outcome()) {
            case BLOCKED -> {
                src.setStatus(SourceStatus.BLOCKED);
                next = Duration.ofHours(24);
                outcome = CheckOutcome.BLOCKED;
            }
            case RATE_LIMITED -> {
                src.setStatus(failures >= props.degradedAfterFailures() ? SourceStatus.FAILING : SourceStatus.DEGRADED);
                Duration ra = result.retryAfter() == null ? Duration.ofMinutes(30) : result.retryAfter();
                next = ra.compareTo(interval) > 0 ? ra : interval;
                outcome = CheckOutcome.RATE_LIMITED;
            }
            default -> {
                src.setStatus(failures >= props.degradedAfterFailures() ? SourceStatus.FAILING : SourceStatus.DEGRADED);
                next = backoff(interval, failures);
                outcome = CheckOutcome.FAILED;
            }
        }
        src.setNextFetchAt(now.plus(next));

        boolean becameProblem = (src.getStatus() == SourceStatus.FAILING || src.getStatus() == SourceStatus.BLOCKED)
                && before != src.getStatus();
        if (becameProblem) {
            String advice = src.getStatus() == SourceStatus.BLOCKED
                    ? " The site does not allow automated monitoring right now; we'll retry daily."
                    : " We'll keep retrying with increasing delays.";
            for (Watch w : watches.findBySourceIdAndActiveTrueOrderByIdAsc(sourceId)) {
                alertService.raiseSystem(w, "MONITORING_ISSUE", "Monitoring problem: " + w.getTitle(),
                        result.message() + advice, src.getStatus().name() + ":" + now.toString().substring(0, 10));
            }
        }
        runs.save(new CheckRun(sourceId, started, now, outcome, result.httpStatus(), result.message(), null, 0));
        return new CheckSummary(sourceId, outcome, 0, result.message());
    }

    Duration backoff(Duration interval, int failures) {
        long factor = 1L << Math.min(Math.max(failures - 1, 0), 12);
        Duration d = interval.multipliedBy(factor);
        return d.compareTo(props.maxBackoff()) > 0 ? props.maxBackoff() : d;
    }

    private static Duration withJitter(Duration interval) {
        long maxJitterMs = interval.toMillis() / 20; // up to 5% spreads load across the interval
        return maxJitterMs <= 0 ? interval : interval.plusMillis(ThreadLocalRandom.current().nextLong(maxJitterMs));
    }
}
