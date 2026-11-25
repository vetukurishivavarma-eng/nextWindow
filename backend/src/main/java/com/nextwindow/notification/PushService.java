package com.nextwindow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextwindow.common.ApiException;
import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.user.User;
import com.nextwindow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int MAX_DEVICES = 10;
    private static final int MAX_FAILURES = 5;

    private final PushSubscriptionRepository subscriptions;
    private final AlertRepository alerts;
    private final UserRepository users;
    private final WebPushSender sender;
    private final ObjectMapper mapper;
    private final NextWindowProperties props;
    private final Clock clock;

    public PushService(PushSubscriptionRepository subscriptions, AlertRepository alerts, UserRepository users,
                       WebPushSender sender, ObjectMapper mapper, NextWindowProperties props, Clock clock) {
        this.subscriptions = subscriptions;
        this.alerts = alerts;
        this.users = users;
        this.sender = sender;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return sender.isConfigured();
    }

    @Transactional
    public PushDtos.SubscriptionResponse subscribe(Long userId, PushDtos.SubscribeRequest req, String userAgent) {
        if (!sender.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PUSH_NOT_CONFIGURED",
                    "Push notifications are not configured on this server. In-app notifications still work.");
        }
        if (!WebPushSender.isAllowedEndpoint(req.endpoint())) {
            throw ApiException.badRequest("INVALID_ENDPOINT", "This push endpoint is not from a supported browser push service.");
        }
        try {
            WebPushCrypto.publicKey(WebPushCrypto.b64(req.keys().p256dh()));
            if (WebPushCrypto.b64(req.keys().auth()).length != 16) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_KEYS", "The browser sent invalid push subscription keys.");
        }
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        PushSubscription sub = subscriptions.findByEndpoint(req.endpoint()).orElse(null);
        if (sub == null) {
            if (subscriptions.countByUserId(userId) >= MAX_DEVICES) {
                throw ApiException.conflict("TOO_MANY_DEVICES", "You can enable push on up to " + MAX_DEVICES + " devices. Remove one first.");
            }
            sub = new PushSubscription(userId, req.endpoint(), req.keys().p256dh(), req.keys().auth(), ua, clock.instant());
        } else {
            // The same browser endpoint re-subscribing (possibly after switching accounts) moves to this user.
            sub.setUserId(userId);
            sub.setPublicKey(req.keys().p256dh());
            sub.setAuthKey(req.keys().auth());
            sub.setUserAgent(ua);
            sub.setFailureCount(0);
        }
        return toDto(subscriptions.save(sub));
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subscriptions.findByEndpointAndUserId(endpoint, userId).ifPresent(subscriptions::delete);
    }

    @Transactional
    public void remove(Long userId, Long id) {
        PushSubscription s = subscriptions.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Device"));
        subscriptions.delete(s);
    }

    @Transactional(readOnly = true)
    public List<PushDtos.SubscriptionResponse> list(Long userId) {
        return subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PushService::toDto).toList();
    }

    @Transactional
    public PushDtos.TestResult sendTest(Long userId) {
        if (!sender.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PUSH_NOT_CONFIGURED",
                    "Push notifications are not configured on this server.");
        }
        List<PushSubscription> subs = subscriptions.findByUserIdOrderByCreatedAtDesc(userId);
        if (subs.isEmpty()) {
            return new PushDtos.TestResult(0, 0, 0, 0, "No devices have push enabled yet. Enable push on this device first.");
        }
        byte[] payload = payload("NextWindow test notification", "Push notifications are working on this device.",
                props.publicUrl() + "/settings", "test", null);
        int[] r = deliver(subs, payload);
        String msg = r[0] == subs.size() ? "Test notification accepted by the push service for all devices."
                : r[0] > 0 ? "Delivered to some devices; others failed." : "The push service did not accept the notification.";
        return new PushDtos.TestResult(subs.size(), r[0], r[1], r[2], msg);
    }

    /** Sends the push for a stored alert and records an honest delivery status. */
    @Transactional
    public void dispatch(Long alertId) {
        Alert alert = alerts.findById(alertId).orElse(null);
        if (alert == null) return;
        if (!sender.isConfigured()) {
            alert.setPushStatus(PushStatus.NOT_CONFIGURED);
            return;
        }
        User user = users.findById(alert.getUserId()).orElse(null);
        if (user == null || !user.isPushEnabled()) {
            alert.setPushStatus(PushStatus.DISABLED);
            return;
        }
        List<PushSubscription> subs = subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (subs.isEmpty()) {
            alert.setPushStatus(PushStatus.NO_SUBSCRIPTIONS);
            return;
        }
        byte[] payload = payload(alert.getTitle(), alert.getMessage(),
                props.publicUrl() + "/watches/" + alert.getWatchId(), "watch-" + alert.getWatchId(), alert.getId());
        int[] r = deliver(subs, payload);
        int attempted = subs.size() - r[2];
        alert.setPushStatus(r[0] == 0 ? (attempted == 0 ? PushStatus.NO_SUBSCRIPTIONS : PushStatus.FAILED)
                : r[0] == attempted ? PushStatus.SENT : PushStatus.PARTIAL);
    }

    /** @return {delivered, failed, removed} */
    private int[] deliver(List<PushSubscription> subs, byte[] payload) {
        int delivered = 0, failed = 0, removed = 0;
        for (PushSubscription s : subs) {
            WebPushSender.Delivery d = sender.send(s, payload);
            switch (d) {
                case DELIVERED -> {
                    delivered++;
                    subscriptions.markDelivered(s.getId(), clock.instant());
                }
                case EXPIRED -> {
                    removed++;
                    subscriptions.deleteQuietly(s.getId());
                }
                default -> {
                    failed++;
                    subscriptions.markFailed(s.getId());
                    if (subscriptions.deleteIfFailedAtLeast(s.getId(), MAX_FAILURES) > 0) {
                        log.info("Removed push subscription {} after repeated failures", s.getId());
                    }
                }
            }
        }
        return new int[]{delivered, failed, removed};
    }

    private byte[] payload(String title, String body, String url, String tag, Long alertId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("body", body.length() > 300 ? body.substring(0, 299) + "…" : body);
        m.put("url", url);
        m.put("tag", tag);
        if (alertId != null) m.put("alertId", alertId);
        try {
            return mapper.writeValueAsString(m).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static PushDtos.SubscriptionResponse toDto(PushSubscription s) {
        String host;
        try {
            host = URI.create(s.getEndpoint()).getHost();
        } catch (IllegalArgumentException e) {
            host = "unknown";
        }
        return new PushDtos.SubscriptionResponse(s.getId(), describe(s.getUserAgent()), host, s.getCreatedAt(),
                s.getLastSuccessAt(), s.getFailureCount());
    }

    static String describe(String ua) {
        if (ua == null) return "Browser";
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Mac OS") ? "macOS" : ua.contains("Windows") ? "Windows" : ua.contains("Linux") ? "Linux" : "";
        return os.isEmpty() ? browser : browser + " on " + os;
    }
}
