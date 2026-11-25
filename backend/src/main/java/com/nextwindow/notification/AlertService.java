package com.nextwindow.notification;

import com.nextwindow.common.Hashing;
import com.nextwindow.condition.Trigger;
import com.nextwindow.watch.AlertMode;
import com.nextwindow.watch.Watch;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Creates alerts with deduplication: identical events (same fingerprint) are never repeated, a per-watch
 * cooldown spaces out alerts, and one-time watches complete after their first alert.
 */
@Service
public class AlertService {
    public enum Outcome { CREATED, DUPLICATE, COOLDOWN, INACTIVE }

    public record Result(Outcome outcome, Alert alert) {}

    private static final Duration DUPLICATE_WINDOW = Duration.ofDays(7);

    private final AlertRepository alerts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlertService(AlertRepository alerts, ApplicationEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.events = events;
        this.clock = clock;
    }

    public static String fingerprint(Long watchId, Trigger t) {
        return Hashing.sha256Hex(watchId + "|" + t.alertType() + "|" + t.fingerprintBasis());
    }

    /** Must run inside the caller's transaction so watch state and alert are committed together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result raise(Watch watch, Trigger trigger) {
        Instant now = clock.instant();
        if (!watch.isActive()) return new Result(Outcome.INACTIVE, null);
        String fp = fingerprint(watch.getId(), trigger);
        if (fp.equals(watch.getLastAlertFingerprint())
                || alerts.existsByWatchIdAndFingerprintAndCreatedAtAfter(watch.getId(), fp, now.minus(DUPLICATE_WINDOW))) {
            return new Result(Outcome.DUPLICATE, null);
        }
        if (watch.getLastTriggeredAt() != null && watch.getCooldownMinutes() > 0
                && watch.getLastTriggeredAt().plus(Duration.ofMinutes(watch.getCooldownMinutes())).isAfter(now)) {
            return new Result(Outcome.COOLDOWN, null);
        }
        Alert alert = alerts.save(new Alert(watch.getUserId(), watch.getId(), trigger.alertType(), trigger.title(),
                trigger.message(), trigger.oldValue(), trigger.newValue(), fp, now));
        watch.setLastTriggeredAt(now);
        watch.setLastAlertFingerprint(fp);
        watch.setUpdatedAt(now);
        if (watch.getAlertMode() == AlertMode.ONE_TIME) {
            watch.setActive(false);
            watch.setCompletedAt(now);
        }
        events.publishEvent(new AlertCreatedEvent(alert.getId()));
        return new Result(Outcome.CREATED, alert);
    }

    /** System alerts (monitoring problems) bypass cooldown but are still deduplicated by fingerprint. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Alert raiseSystem(Watch watch, String type, String title, String message, String basis) {
        Instant now = clock.instant();
        String fp = Hashing.sha256Hex(watch.getId() + "|" + type + "|" + basis);
        if (alerts.existsByWatchIdAndFingerprintAndCreatedAtAfter(watch.getId(), fp, now.minus(Duration.ofDays(1)))) {
            return null;
        }
        Alert alert = alerts.save(new Alert(watch.getUserId(), watch.getId(), type, title, message, null, null, fp, now));
        events.publishEvent(new AlertCreatedEvent(alert.getId()));
        return alert;
    }
}
