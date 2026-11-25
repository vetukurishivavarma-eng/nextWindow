package com.nextwindow.monitoring;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.notification.AlertDtos;
import com.nextwindow.notification.AlertQueryService;
import com.nextwindow.notification.AlertRepository;
import com.nextwindow.watch.WatchDtos;
import com.nextwindow.watch.WatchRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    public record Dashboard(long totalWatches, long activeWatches, long pausedWatches, long alertsLast7Days, long unreadAlerts,
                            List<AlertDtos.AlertResponse> recentAlerts, List<WatchDtos.CheckRunResponse> recentChanges,
                            MonitoringQueryService.Health health) {}

    private final WatchRepository watches;
    private final AlertRepository alerts;
    private final AlertQueryService alertQueries;
    private final MonitoringQueryService monitoring;
    private final Clock clock;

    public DashboardController(WatchRepository watches, AlertRepository alerts, AlertQueryService alertQueries,
                               MonitoringQueryService monitoring, Clock clock) {
        this.watches = watches;
        this.alerts = alerts;
        this.alertQueries = alertQueries;
        this.monitoring = monitoring;
        this.clock = clock;
    }

    @GetMapping
    public Dashboard get(@AuthenticationPrincipal AuthUser user) {
        long total = watches.countByUserId(user.id());
        long active = watches.countByUserIdAndActive(user.id(), true);
        return new Dashboard(total, active, total - active,
                alerts.countByUserIdAndCreatedAtAfter(user.id(), clock.instant().minus(Duration.ofDays(7))),
                alerts.countByUserIdAndReadFalse(user.id()),
                alertQueries.recent(user.id(), 5),
                monitoring.checks(user.id(), "changes", 0, 6).items(),
                monitoring.health(user.id()));
    }
}
