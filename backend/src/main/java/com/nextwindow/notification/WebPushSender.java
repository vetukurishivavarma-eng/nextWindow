package com.nextwindow.notification;

import com.nextwindow.config.NextWindowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Delivers encrypted Web Push messages to browser push services using VAPID authentication. */
@Component
public class WebPushSender {
    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    /** Push endpoints are only accepted for known browser push services (prevents SSRF via subscriptions). */
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com", "android.googleapis.com", "push.services.mozilla.com",
            "push.apple.com", "notify.windows.com");

    public enum Delivery { DELIVERED, EXPIRED, FAILED, NOT_CONFIGURED }

    public interface Transport {
        int post(URI endpoint, byte[] body, String authorization, int ttlSeconds) throws Exception;
    }

    private final NextWindowProperties.Push props;
    private final Clock clock;
    private final Transport transport;
    private volatile boolean keysValid;

    @Autowired
    public WebPushSender(NextWindowProperties properties, Clock clock) {
        this(properties, clock, defaultTransport());
    }

    WebPushSender(NextWindowProperties properties, Clock clock, Transport transport) {
        this.props = properties.push();
        this.clock = clock;
        this.transport = transport;
        if (props.configured()) {
            try {
                WebPushCrypto.validateVapidKeys(props.vapidPublicKey(), props.vapidPrivateKey());
                keysValid = true;
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.error("Web Push disabled: VAPID keys are invalid ({}). In-app notifications still work.", e.getMessage());
            }
        } else {
            log.info("Web Push not configured (VAPID keys missing). In-app notifications still work.");
        }
    }

    public boolean isConfigured() {
        return keysValid;
    }

    public static boolean isAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) return false;
            if (uri.getPort() != -1 && uri.getPort() != 443) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Delivery send(PushSubscription sub, byte[] payload) {
        if (!keysValid) return Delivery.NOT_CONFIGURED;
        if (!isAllowedEndpoint(sub.getEndpoint())) return Delivery.FAILED;
        try {
            URI endpoint = URI.create(sub.getEndpoint());
            byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.b64(sub.getPublicKey()), WebPushCrypto.b64(sub.getAuthKey()));
            String audience = endpoint.getScheme() + "://" + endpoint.getHost();
            String auth = WebPushCrypto.vapidHeader(audience, props.subject(), clock.instant().plus(Duration.ofHours(12)).getEpochSecond(),
                    props.vapidPublicKey(), props.vapidPrivateKey());
            int status = transport.post(endpoint, body, auth, props.ttlSeconds());
            if (status >= 200 && status < 300) return Delivery.DELIVERED;
            if (status == 404 || status == 410) return Delivery.EXPIRED;
            log.warn("Push service rejected message with HTTP {}", status);
            return Delivery.FAILED;
        } catch (Exception e) {
            log.warn("Push delivery failed: {}", e.getClass().getSimpleName());
            return Delivery.FAILED;
        }
    }

    private static Transport defaultTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (endpoint, body, authorization, ttl) -> {
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authorization)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", Integer.toString(ttl))
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        };
    }
}
