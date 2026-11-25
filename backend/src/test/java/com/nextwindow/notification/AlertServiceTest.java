package com.nextwindow.notification;

import com.nextwindow.condition.Trigger;
import com.nextwindow.watch.AlertMode;
import com.nextwindow.watch.Watch;
import com.nextwindow.watch.WatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertServiceTest {
    private final AlertRepository repo = mock(AlertRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final AlertService service = new AlertService(repo, events, Clock.fixed(now, ZoneOffset.UTC));
    private Watch watch;
    private final Trigger trigger = new Trigger("PRICE_DROP", "Price dropped", "msg", "$10", "$9", "drop:10->9");

    @BeforeEach
    void setUp() {
        watch = new Watch(1L, 2L, "Lamp", WatchType.PRICE_DROP, now);
        ReflectionTestUtils.setField(watch, "id", 7L);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createsAlertAndPublishesEvent() {
        var r = service.raise(watch, trigger);
        assertThat(r.outcome()).isEqualTo(AlertService.Outcome.CREATED);
        assertThat(r.alert().getUserId()).isEqualTo(1L);
        assertThat(r.alert().getWatchId()).isEqualTo(7L);
        assertThat(watch.getLastTriggeredAt()).isEqualTo(now);
        assertThat(watch.getLastAlertFingerprint()).isEqualTo(AlertService.fingerprint(7L, trigger));
        verify(events).publishEvent(any(AlertCreatedEvent.class));
    }

    @Test
    void suppressesIdenticalEvent() {
        watch.setLastAlertFingerprint(AlertService.fingerprint(7L, trigger));
        assertThat(service.raise(watch, trigger).outcome()).isEqualTo(AlertService.Outcome.DUPLICATE);
        when(repo.existsByWatchIdAndFingerprintAndCreatedAtAfter(eq(7L), anyString(), any())).thenReturn(true);
        watch.setLastAlertFingerprint(null);
        assertThat(service.raise(watch, trigger).outcome()).isEqualTo(AlertService.Outcome.DUPLICATE);
        verify(repo, never()).save(any());
    }

    @Test
    void respectsCooldown() {
        watch.setCooldownMinutes(60);
        watch.setLastTriggeredAt(now.minusSeconds(600));
        assertThat(service.raise(watch, trigger).outcome()).isEqualTo(AlertService.Outcome.COOLDOWN);
        watch.setLastTriggeredAt(now.minusSeconds(3700));
        assertThat(service.raise(watch, trigger).outcome()).isEqualTo(AlertService.Outcome.CREATED);
    }

    @Test
    void oneTimeModeCompletesWatchAndPausedWatchesNeverAlert() {
        watch.setAlertMode(AlertMode.ONE_TIME);
        service.raise(watch, trigger);
        assertThat(watch.isActive()).isFalse();
        assertThat(watch.getCompletedAt()).isEqualTo(now);
        assertThat(service.raise(watch, new Trigger("PRICE_DROP", "t", "m", null, null, "other")).outcome())
                .isEqualTo(AlertService.Outcome.INACTIVE);
    }
}
