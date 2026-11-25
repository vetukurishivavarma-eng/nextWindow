package com.nextwindow.scheduler;

import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.monitoring.MonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls for due sources and processes them on a bounded worker pool. Claiming uses database row
 * leases, so several application instances can run this scheduler without double-processing.
 */
@Component
@ConditionalOnProperty(prefix = "nextwindow.monitoring", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final MonitoringService monitoring;
    private final ThreadPoolTaskExecutor executor;
    private final NextWindowProperties.Monitoring props;
    private final Clock clock;
    private final AtomicReference<Instant> lastTick = new AtomicReference<>();

    public MonitoringScheduler(MonitoringService monitoring, @Qualifier("monitoringExecutor") ThreadPoolTaskExecutor executor,
                               NextWindowProperties properties, Clock clock) {
        this.monitoring = monitoring;
        this.executor = executor;
        this.props = properties.monitoring();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nextwindow.monitoring.tick-interval:PT15S}", initialDelayString = "PT10S")
    public void tick() {
        lastTick.set(clock.instant());
        int capacity = executor.getMaxPoolSize() * 2 - executor.getQueueSize() - executor.getActiveCount();
        if (capacity <= 0) return;
        List<Long> claimed;
        try {
            claimed = monitoring.claimDue(Math.min(capacity, props.batchSize()));
        } catch (RuntimeException e) {
            log.warn("Could not claim due sources: {}", e.getMessage());
            return;
        }
        for (Long id : claimed) {
            try {
                executor.execute(() -> monitoring.runLeased(id));
            } catch (RuntimeException e) {
                // Lease will expire and the source will be picked up again.
                log.warn("Executor rejected source {}", id);
            }
        }
        if (!claimed.isEmpty()) log.debug("Dispatched {} source checks", claimed.size());
    }

    public Instant lastTick() {
        return lastTick.get();
    }
}
