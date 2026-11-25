package com.nextwindow.notification;

import com.nextwindow.common.ApiException;
import com.nextwindow.common.PageResponse;
import com.nextwindow.common.Pagination;
import com.nextwindow.source.MonitoringSource;
import com.nextwindow.source.MonitoringSourceRepository;
import com.nextwindow.watch.Watch;
import com.nextwindow.watch.WatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertQueryService {
    private final AlertRepository alerts;
    private final WatchRepository watches;
    private final MonitoringSourceRepository sources;

    public AlertQueryService(AlertRepository alerts, WatchRepository watches, MonitoringSourceRepository sources) {
        this.alerts = alerts;
        this.watches = watches;
        this.sources = sources;
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertDtos.AlertResponse> list(Long userId, boolean unreadOnly, Long watchId, int page, int size) {
        var pageable = Pagination.of(page, size, Sort.unsorted());
        Page<Alert> p;
        if (watchId != null) {
            watches.findByIdAndUserId(watchId, userId).orElseThrow(() -> ApiException.notFound("Watch"));
            p = alerts.findByUserIdAndWatchIdOrderByCreatedAtDescIdDesc(userId, watchId, pageable);
        } else if (unreadOnly) {
            p = alerts.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, pageable);
        } else {
            p = alerts.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        }
        return PageResponse.of(p, mapper(p.getContent()));
    }

    @Transactional(readOnly = true)
    public List<AlertDtos.AlertResponse> recent(Long userId, int n) {
        List<Alert> list = alerts.findByUserIdOrderByCreatedAtDescIdDesc(userId, Pagination.of(0, n, Sort.unsorted())).getContent();
        return list.stream().map(mapper(list)).toList();
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return alerts.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public AlertDtos.AlertResponse setRead(Long userId, Long alertId, boolean read) {
        Alert a = alerts.findByIdAndUserId(alertId, userId).orElseThrow(() -> ApiException.notFound("Alert"));
        a.setRead(read);
        return mapper(List.of(a)).apply(a);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return alerts.markAllRead(userId);
    }

    private Function<Alert, AlertDtos.AlertResponse> mapper(List<Alert> list) {
        Set<Long> watchIds = list.stream().map(Alert::getWatchId).collect(Collectors.toSet());
        Map<Long, Watch> ws = watches.findAllById(watchIds).stream().collect(Collectors.toMap(Watch::getId, w -> w));
        Map<Long, String> urls = new HashMap<>();
        sources.findAllById(ws.values().stream().map(Watch::getSourceId).collect(Collectors.toSet()))
                .forEach(s -> urls.put(s.getId(), displayUrl(s)));
        return a -> {
            Watch w = ws.get(a.getWatchId());
            return new AlertDtos.AlertResponse(a.getId(), a.getWatchId(), w == null ? null : w.getTitle(),
                    w == null ? null : urls.get(w.getSourceId()), a.getAlertType(), a.getTitle(), a.getMessage(),
                    a.getOldValue(), a.getNewValue(), a.isRead(), a.getPushStatus().name(), a.getCreatedAt());
        };
    }

    public static String displayUrl(MonitoringSource s) {
        return s.getCanonicalUrl();
    }
}
