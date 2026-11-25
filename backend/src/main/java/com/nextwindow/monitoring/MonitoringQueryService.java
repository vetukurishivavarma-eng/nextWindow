package com.nextwindow.monitoring;

import com.nextwindow.common.PageResponse;
import com.nextwindow.common.Pagination;
import com.nextwindow.history.CheckOutcome;
import com.nextwindow.history.CheckRun;
import com.nextwindow.history.CheckRunRepository;
import com.nextwindow.source.MonitoringSource;
import com.nextwindow.source.MonitoringSourceRepository;
import com.nextwindow.source.SourceStatus;
import com.nextwindow.watch.Watch;
import com.nextwindow.watch.WatchDtos;
import com.nextwindow.watch.WatchRepository;
import com.nextwindow.watch.WatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonitoringQueryService {

    public record Health(int sources, int healthy, int pending, int degraded, int failing, int blocked,
                         Instant lastCheckAt, Instant nextCheckAt, boolean schedulerRunning, Instant schedulerLastTick,
                         List<AttentionItem> attention) {}

    public record AttentionItem(Long watchId, String watchTitle, String status, String message, Instant since) {}

    private final WatchRepository watches;
    private final MonitoringSourceRepository sources;
    private final CheckRunRepository runs;
    private final Optional<com.nextwindow.scheduler.MonitoringScheduler> scheduler;

    public MonitoringQueryService(WatchRepository watches, MonitoringSourceRepository sources, CheckRunRepository runs,
                                  Optional<com.nextwindow.scheduler.MonitoringScheduler> scheduler) {
        this.watches = watches;
        this.sources = sources;
        this.runs = runs;
        this.scheduler = scheduler;
    }

    @Transactional(readOnly = true)
    public Health health(Long userId) {
        List<Watch> mine = watches.findByUserId(userId);
        Map<Long, MonitoringSource> srcs = sources.findAllById(mine.stream().map(Watch::getSourceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MonitoringSource::getId, Function.identity()));
        int healthy = 0, pending = 0, degraded = 0, failing = 0, blocked = 0;
        Instant last = null, next = null;
        Set<Long> activeSources = mine.stream().filter(Watch::isActive).map(Watch::getSourceId).collect(Collectors.toSet());
        for (MonitoringSource s : srcs.values()) {
            switch (s.getStatus()) {
                case ACTIVE -> healthy++;
                case PENDING -> pending++;
                case DEGRADED -> degraded++;
                case FAILING -> failing++;
                case BLOCKED, DISABLED -> blocked++;
            }
            if (s.getLastFetchedAt() != null && (last == null || s.getLastFetchedAt().isAfter(last))) last = s.getLastFetchedAt();
            if (activeSources.contains(s.getId()) && (next == null || s.getNextFetchAt().isBefore(next))) next = s.getNextFetchAt();
        }
        List<AttentionItem> attention = new ArrayList<>();
        for (Watch w : mine) {
            MonitoringSource s = srcs.get(w.getSourceId());
            if (s == null) continue;
            if (w.isActive() && EnumSet.of(SourceStatus.DEGRADED, SourceStatus.FAILING, SourceStatus.BLOCKED).contains(s.getStatus())) {
                attention.add(new AttentionItem(w.getId(), w.getTitle(), s.getStatus().name(),
                        s.getStatusMessage() == null ? "Recent checks failed." : s.getStatusMessage(), s.getLastFetchedAt()));
            } else if (!w.isActive() && w.getCompletedAt() != null) {
                attention.add(new AttentionItem(w.getId(), w.getTitle(), "COMPLETED",
                        w.getExpiresAt() != null && !w.getExpiresAt().isAfter(w.getCompletedAt()) ? "This watch expired." :
                                "One-time alert sent. Resume to keep watching.", w.getCompletedAt()));
            }
        }
        attention.sort(Comparator.comparing(AttentionItem::since, Comparator.nullsLast(Comparator.reverseOrder())));
        Instant tick = scheduler.map(com.nextwindow.scheduler.MonitoringScheduler::lastTick).orElse(null);
        return new Health(srcs.size(), healthy, pending, degraded, failing, blocked, last, next,
                scheduler.isPresent(), tick, attention.size() > 20 ? attention.subList(0, 20) : attention);
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchDtos.CheckRunResponse> checks(Long userId, String filter, int page, int size) {
        List<Watch> mine = watches.findByUserId(userId);
        if (mine.isEmpty()) return new PageResponse<>(List.of(), 0, size, 0, 0);
        Map<Long, Watch> bySource = new HashMap<>();
        mine.stream().sorted(Comparator.comparing(Watch::getId)).forEach(w -> bySource.putIfAbsent(w.getSourceId(), w));
        Map<Long, String> urls = sources.findAllById(bySource.keySet()).stream()
                .collect(Collectors.toMap(MonitoringSource::getId, MonitoringSource::getCanonicalUrl));
        var pageable = Pagination.of(page, size, Sort.unsorted());
        Page<CheckRun> p = switch (filter == null ? "all" : filter) {
            case "changes" -> runs.findBySourceIdsAndOutcomes(bySource.keySet(), List.of(CheckOutcome.CHANGED, CheckOutcome.BASELINE), pageable);
            case "failures" -> runs.findBySourceIdsAndOutcomes(bySource.keySet(),
                    List.of(CheckOutcome.FAILED, CheckOutcome.BLOCKED, CheckOutcome.RATE_LIMITED), pageable);
            default -> runs.findBySourceIds(bySource.keySet(), pageable);
        };
        return PageResponse.of(p, c -> WatchService.checkRun(c, urls.get(c.getSourceId()), bySource.get(c.getSourceId())));
    }
}
