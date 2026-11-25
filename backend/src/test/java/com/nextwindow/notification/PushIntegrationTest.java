package com.nextwindow.notification;

import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.demo.DemoService;
import com.nextwindow.monitoring.MonitoringService;
import com.nextwindow.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PushIntegrationTest extends IntegrationTest {
    static final String PUB;
    static final String PRIV;
    static final AtomicInteger NEXT_STATUS = new AtomicInteger(201);
    static final List<String> SENT_TO = new CopyOnWriteArrayList<>();

    static {
        try {
            KeyPair kp = WebPushCrypto.generateKeyPair();
            PUB = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) kp.getPublic()));
            byte[] d = ((ECPrivateKey) kp.getPrivate()).getS().toByteArray();
            byte[] d32 = new byte[32];
            System.arraycopy(d, Math.max(0, d.length - 32), d32, Math.max(0, 32 - d.length), Math.min(32, d.length));
            PRIV = WebPushCrypto.b64(d32);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void vapid(DynamicPropertyRegistry r) {
        r.add("nextwindow.push.vapid-public-key", () -> PUB);
        r.add("nextwindow.push.vapid-private-key", () -> PRIV);
    }

    @TestConfiguration
    static class FakePushTransport {
        @Bean
        @Primary
        WebPushSender fakeSender(NextWindowProperties props, Clock clock) {
            return new WebPushSender(props, clock, (endpoint, body, auth, ttl) -> {
                assertThat(auth).startsWith("vapid t=");
                SENT_TO.add(endpoint.toString());
                return NEXT_STATUS.get();
            });
        }
    }

    @Autowired MonitoringService monitoring;
    @Autowired DemoService demo;
    @Autowired PushService push;

    private String browserKey() throws Exception {
        return WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) WebPushCrypto.generateKeyPair().getPublic()));
    }

    private String subscription(String endpoint) throws Exception {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + browserKey() + "\",\"auth\":\"BTBZMqHH6r4Tts7J_aSIgg\"}}";
    }

    @Test
    void subscribeTestDispatchAndExpiry() throws Exception {
        SENT_TO.clear();
        NEXT_STATUS.set(201);
        Cookie u = register("push@example.com");
        Cookie other = register("push2@example.com");
        get(null, "/api/config").andExpect(jsonPath("$.pushConfigured").value(true)).andExpect(jsonPath("$.vapidPublicKey").value(PUB));

        post(u, "/api/push/subscribe", subscription("https://evil.example.com/steal")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENDPOINT"));
        post(u, "/api/push/subscribe", "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\",\"keys\":{\"p256dh\":\"bad\",\"auth\":\"bad\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_KEYS"));
        long subId = json(post(u, "/api/push/subscribe", subscription("https://fcm.googleapis.com/fcm/send/device-1"))
                .andExpect(status().isOk())).get("id").asLong();
        get(u, "/api/push/subscriptions").andExpect(jsonPath("$[0].endpointHost").value("fcm.googleapis.com"));
        send("DELETE", other, "/api/push/subscriptions/" + subId, null).andExpect(status().isNotFound());

        post(u, "/api/push/test", null).andExpect(jsonPath("$.delivered").value(1)).andExpect(jsonPath("$.failed").value(0));
        assertThat(SENT_TO).containsExactly("https://fcm.googleapis.com/fcm/send/device-1");

        Long src = sourceIdOf(createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\",\"cooldownMinutes\":0}"));
        monitoring.checkNow(src);
        demo.simulate("headphones", DemoService.Action.PRICE_DROP, null);
        monitoring.checkNow(src);
        Long alertId = jdbc.queryForObject("select id from alert", Long.class);
        push.dispatch(alertId); // synchronously, independent of the async listener
        assertThat(jdbc.queryForObject("select push_status from alert", String.class)).isEqualTo("SENT");

        // Push service reports the subscription expired: it is removed and failure is recorded honestly
        NEXT_STATUS.set(410);
        push.dispatch(alertId);
        assertThat(jdbc.queryForObject("select push_status from alert", String.class)).isEqualTo("NO_SUBSCRIPTIONS");
        assertThat(jdbc.queryForObject("select count(*) from push_subscription", Integer.class)).isZero();

        NEXT_STATUS.set(500);
        post(u, "/api/push/subscribe", subscription("https://fcm.googleapis.com/fcm/send/device-2")).andExpect(status().isOk());
        push.dispatch(alertId);
        assertThat(jdbc.queryForObject("select push_status from alert", String.class)).isEqualTo("FAILED");

        // User turned push off
        send("PUT", u, "/api/settings", "{\"pushEnabled\":false}").andExpect(jsonPath("$.pushEnabled").value(false));
        push.dispatch(alertId);
        assertThat(jdbc.queryForObject("select push_status from alert", String.class)).isEqualTo("DISABLED");

        post(u, "/api/push/unsubscribe", "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/device-2\"}").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from push_subscription", Integer.class)).isZero();
    }
}
