package com.nextwindow.scheduler;

import com.nextwindow.auth.SessionService;
import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.history.CheckRunRepository;
import com.nextwindow.history.SnapshotRepository;
import com.nextwindow.watch.WatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/** Housekeeping: expires watches and keeps history tables bounded. */
@Component
public class MaintenanceJobs {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final WatchRepository watches;
    private final SnapshotRepository snapshots;
    private final CheckRunRepository runs;
    private final SessionService sessions;
    private final NextWindowProperties.Monitoring props;
    private final Clock clock;

    public MaintenanceJobs(WatchRepository watches, SnapshotRepository snapshots, CheckRunRepository runs,
                           SessionService sessions, NextWindowProperties properties, Clock clock) {
        this.watches = watches;
        this.snapshots = snapshots;
        this.runs = runs;
        this.sessions = sessions;
        this.props = properties.monitoring();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void expireWatches() {
        int n = watches.expireDue(clock.instant());
        if (n > 0) log.info("Expired {} watches", n);
    }

    @Scheduled(cron = "${nextwindow.maintenance.cron:0 17 3 * * *}")
    @Transactional
    public void pruneHistory() {
        int r = runs.deleteOlderThan(clock.instant().minus(Duration.ofDays(props.checkRunRetentionDays())));
        int s = snapshots.pruneBeyond(props.snapshotsPerSource());
        int a = sessions.purgeExpired();
        log.info("Maintenance: removed {} check runs, {} snapshots, {} expired sessions", r, s, a);
    }
}
