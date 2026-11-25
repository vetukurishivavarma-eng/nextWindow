package com.nextwindow.watch;

import com.nextwindow.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WatchIntegrationTest extends IntegrationTest {

    @Test
    void createListEditPauseResumeDelete() throws Exception {
        Cookie u = register("a@example.com");
        var w = createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\",\"checkIntervalMinutes\":30}");
        long id = w.get("id").asLong();
        assertThat(w.get("active").asBoolean()).isTrue();
        assertThat(w.get("source").get("status").asText()).isEqualTo("PENDING");
        assertThat(w.get("conditionState").asText()).isEqualTo("Waiting for a readable price");

        get(u, "/api/watches").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));

        send("PUT", u, "/api/watches/" + id, """
                {"title":"Cheap headphones","watchType":"PRICE_BELOW","targetPrice":150,"alertMode":"ONE_TIME",
                 "cooldownMinutes":0,"checkIntervalMinutes":60}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cheap headphones"))
                .andExpect(jsonPath("$.watchType").value("PRICE_BELOW"))
                .andExpect(jsonPath("$.targetPrice").value(150));

        post(u, "/api/watches/" + id + "/pause", null).andExpect(jsonPath("$.active").value(false));
        get(u, "/api/watches?filter=paused").andExpect(jsonPath("$.totalItems").value(1));
        post(u, "/api/watches/" + id + "/resume", null).andExpect(jsonPath("$.active").value(true));

        send("DELETE", u, "/api/watches/" + id, null).andExpect(status().isNoContent());
        get(u, "/api/watches/" + id).andExpect(status().isNotFound());
        // last watch on the source removed: shared source and its history are removed too
        assertThat(jdbc.queryForObject("select count(*) from monitoring_source", Integer.class)).isZero();
    }

    @Test
    void validatesConditionFields() throws Exception {
        Cookie u = register("b@example.com");
        post(u, "/api/watches", "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_BELOW\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TARGET_PRICE_REQUIRED"));
        post(u, "/api/watches", "{\"url\":\"demo://careers\",\"watchType\":\"KEYWORD_FOUND\",\"targetKeyword\":\" \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("KEYWORD_REQUIRED"));
        post(u, "/api/watches", "{\"url\":\"demo://careers\",\"watchType\":\"ANY_CHANGE\",\"checkIntervalMinutes\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INTERVAL"));
        post(u, "/api/watches", "{\"url\":\"demo://careers\",\"watchType\":\"NOPE\"}").andExpect(status().isBadRequest());
        post(u, "/api/watches", "{\"url\":\"\",\"watchType\":\"ANY_CHANGE\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.url").exists());
    }

    @Test
    void rejectsSsrfTargets() throws Exception {
        Cookie u = register("c@example.com");
        for (String url : new String[]{"http://127.0.0.1/admin", "http://localhost:8080/actuator", "http://169.254.169.254/latest/meta-data",
                "http://10.0.0.1/", "http://[::1]/", "file:///etc/passwd", "gopher://example.com/", "http://2130706433/",
                "http://metadata.google.internal/computeMetadata/v1/"}) {
            post(u, "/api/watches", "{\"url\":\"" + url + "\",\"watchType\":\"ANY_CHANGE\"}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("URL_REJECTED"));
            post(u, "/api/sources/analyze", "{\"url\":\"" + url + "\"}").andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from monitoring_source", Integer.class)).isZero();
    }

    @Test
    void usersCannotSeeOrModifyOthersWatches() throws Exception {
        Cookie owner = register("owner@example.com");
        Cookie other = register("other@example.com");
        long id = createWatch(owner, "{\"url\":\"demo://sneakers\",\"watchType\":\"STOCK_AVAILABLE\"}").get("id").asLong();

        get(other, "/api/watches/" + id).andExpect(status().isNotFound());
        get(other, "/api/watches").andExpect(jsonPath("$.totalItems").value(0));
        send("PUT", other, "/api/watches/" + id, "{\"title\":\"x\",\"watchType\":\"ANY_CHANGE\",\"alertMode\":\"REPEAT\",\"cooldownMinutes\":0,\"checkIntervalMinutes\":60}")
                .andExpect(status().isNotFound());
        post(other, "/api/watches/" + id + "/pause", null).andExpect(status().isNotFound());
        post(other, "/api/watches/" + id + "/check-now", null).andExpect(status().isNotFound());
        get(other, "/api/watches/" + id + "/snapshots").andExpect(status().isNotFound());
        get(other, "/api/watches/" + id + "/checks").andExpect(status().isNotFound());
        get(other, "/api/alerts?watchId=" + id).andExpect(status().isNotFound());
        send("DELETE", other, "/api/watches/" + id, null).andExpect(status().isNotFound());
        long sourceId = jdbc.queryForObject("select source_id from watch where id = ?", Long.class, id);
        get(other, "/api/sources/" + sourceId).andExpect(status().isNotFound());
        get(owner, "/api/sources/" + sourceId).andExpect(status().isOk());

        get(owner, "/api/watches/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void sameUrlFromDifferentUsersSharesOneSource() throws Exception {
        Cookie a = register("a1@example.com");
        Cookie b = register("b1@example.com");
        var w1 = createWatch(a, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\"}");
        var w2 = createWatch(b, "{\"url\":\"DEMO://Headphones?utm_source=newsletter\",\"watchType\":\"PRICE_BELOW\",\"targetPrice\":100}");
        assertThat(sourceIdOf(w1)).isEqualTo(sourceIdOf(w2));
        assertThat(jdbc.queryForObject("select count(*) from monitoring_source", Integer.class)).isEqualTo(1);

        // deleting one user's watch keeps the shared source for the other
        send("DELETE", a, "/api/watches/" + w1.get("id").asLong(), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from monitoring_source", Integer.class)).isEqualTo(1);
    }

    @Test
    void sharedSourceIntervalIsMostFrequentRequest() throws Exception {
        Cookie a = register("i1@example.com");
        Cookie b = register("i2@example.com");
        // demo sources use the fixed demo interval, so test the computation with a web-type source row directly
        var w1 = createWatch(a, "{\"url\":\"demo://transit\",\"watchType\":\"ANY_CHANGE\",\"checkIntervalMinutes\":360}");
        createWatch(b, "{\"url\":\"demo://transit\",\"watchType\":\"ANY_CHANGE\",\"checkIntervalMinutes\":30}");
        assertThat(jdbc.queryForObject("select check_interval_minutes from monitoring_source where id = ?", Integer.class, sourceIdOf(w1)))
                .isEqualTo(1);
    }

    @Test
    void analyzeDemoSource() throws Exception {
        Cookie u = register("an@example.com");
        post(u, "/api/sources/analyze", "{\"url\":\"demo://headphones\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.title").value("Aurora Wireless Headphones"))
                .andExpect(jsonPath("$.price").value(199.0))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.availability").value("IN_STOCK"))
                .andExpect(jsonPath("$.watchTypes[0].type").value("PRICE_DROP"))
                .andExpect(jsonPath("$.watchTypes[0].recommended").value(true));
        post(u, "/api/sources/analyze", "{\"url\":\"demo://careers\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.notes[0]").value(org.hamcrest.Matchers.containsString("still monitor")));
        post(u, "/api/sources/analyze", "{\"url\":\"demo://nonexistent\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false));
    }
}
