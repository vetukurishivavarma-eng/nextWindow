package com.nextwindow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends push notifications after the alert has been committed, off the monitoring thread. */
@Component
public class PushDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);
    private final PushService pushService;

    public PushDispatcher(PushService pushService) {
        this.pushService = pushService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertCreated(AlertCreatedEvent event) {
        try {
            pushService.dispatch(event.alertId());
        } catch (RuntimeException e) {
            log.warn("Push dispatch for alert {} failed: {}", event.alertId(), e.getMessage());
        }
    }
}
