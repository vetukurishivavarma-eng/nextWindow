package com.nextwindow.notification;

import com.nextwindow.demo.DemoService;
import com.nextwindow.monitoring.MonitoringService;
import com.nextwindow.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AlertsIntegrationTest extends IntegrationTest {
    @Autowired MonitoringService monitoring;
    @Autowired DemoService demo;

    @Test
    void notificationCenterReadStateAndOwnership() throws Exception {
        Cookie u = register("n@example.com");
        Cookie other = register("o@example.com");
        Long src = sourceIdOf(createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\",\"cooldownMinutes\":0}"));
        monitoring.checkNow(src);
        demo.simulate("headphones", DemoService.Action.PRICE_DROP, null);
        monitoring.checkNow(src);
        demo.simulate("headphones", DemoService.Action.PRICE_DROP, null);
        monitoring.checkNow(src);

        var list = json(get(u, "/api/alerts?size=10"));
        long first = list.get("items").get(0).get("id").asLong();
        get(u, "/api/alerts").andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].sourceUrl").value("demo://headphones"))
                .andExpect(jsonPath("$.items[0].pushStatus").value("NOT_CONFIGURED"));
        get(u, "/api/alerts/unread-count").andExpect(jsonPath("$.unread").value(2));

        post(other, "/api/alerts/" + first + "/read", null).andExpect(status().isNotFound());
        get(other, "/api/alerts").andExpect(jsonPath("$.totalItems").value(0));

        post(u, "/api/alerts/" + first + "/read", null).andExpect(jsonPath("$.read").value(true));
        get(u, "/api/alerts/unread").andExpect(jsonPath("$.totalItems").value(1));
        post(u, "/api/alerts/" + first + "/unread", null).andExpect(jsonPath("$.read").value(false));
        post(u, "/api/alerts/read-all", null).andExpect(jsonPath("$.updated").value(2));
        get(u, "/api/alerts/unread-count").andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void pushEndpointsExplainWhenNotConfigured() throws Exception {
        Cookie u = register("p@example.com");
        post(u, "/api/push/subscribe", "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PUSH_NOT_CONFIGURED"));
        post(u, "/api/push/test", null).andExpect(status().isServiceUnavailable());
        get(u, "/api/settings").andExpect(jsonPath("$.pushConfigured").value(false));
    }
}
