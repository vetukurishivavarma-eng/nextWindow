package com.nextwindow.monitoring;

import com.nextwindow.demo.DemoService;
import com.nextwindow.history.CheckOutcome;
import com.nextwindow.scheduler.MonitoringScheduler;
import com.nextwindow.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonitoringIntegrationTest extends IntegrationTest {
    @Autowired MonitoringService monitoring;
    @Autowired DemoService demo;
    @Autowired @Qualifier("monitoringExecutor") ThreadPoolTaskExecutor executor;
    @Autowired com.nextwindow.config.NextWindowProperties props;

    private MonitoringService.CheckSummary check(Long sourceId) {
        return monitoring.checkNow(sourceId).orElseThrow();
    }

    private int alerts() {
        return jdbc.queryForObject("select count(*) from alert", Integer.class);
    }

    @Test
    void baselineDoesNotAlertAndChangesDo() throws Exception {
        Cookie u = register("m1@example.com");
        var w = createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\",\"cooldownMinutes\":0}");
        Long src = sourceIdOf(w);

        assertThat(check(src).outcome()).isEqualTo(CheckOutcome.BASELINE);
        assertThat(alerts()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from snapshot", Integer.class)).isEqualTo(1);

        // Volatile timestamp and request id change on every render but must not count as a change.
        assertThat(check(src).outcome()).isEqualTo(CheckOutcome.UNCHANGED);
        assertThat(jdbc.queryForObject("select count(*) from snapshot", Integer.class)).isEqualTo(1);

        demo.simulate("headphones", DemoService.Action.PRICE_DROP, null);
        var changed = check(src);
        assertThat(changed.outcome()).isEqualTo(CheckOutcome.CHANGED);
        assertThat(changed.alertsCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select old_value || ' -> ' || new_value from alert", String.class)).isEqualTo("$199.00 -> $169.15");

        // Same state again: no duplicate alert
        assertThat(check(src).alertsCreated()).isZero();
        assertThat(alerts()).isEqualTo(1);

        get(u, "/api/watches/" + w.get("id").asLong()).andExpect(jsonPath("$.current.price").value(169.15))
                .andExpect(jsonPath("$.previous.price").value(199.0))
                .andExpect(jsonPath("$.title").value("Aurora Wireless Headphones"));
        get(u, "/api/watches/" + w.get("id").asLong() + "/snapshots").andExpect(jsonPath("$.totalItems").value(2));
        get(u, "/api/watches/" + w.get("id").asLong() + "/checks").andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void oneSourceFetchEvaluatesEveryUsersWatch() throws Exception {
        Cookie a = register("s1@example.com");
        Cookie b = register("s2@example.com");
        var wa = createWatch(a, "{\"url\":\"demo://sneakers\",\"watchType\":\"STOCK_AVAILABLE\",\"cooldownMinutes\":0}");
        var wb = createWatch(b, "{\"url\":\"demo://sneakers\",\"watchType\":\"STATUS_CHANGE\",\"cooldownMinutes\":0}");
        Long src = sourceIdOf(wa);
        check(src);
        demo.simulate("sneakers", DemoService.Action.RESTOCK, null);
        var r = check(src);
        assertThat(r.alertsCreated()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from check_run where source_id = ?", Integer.class, src)).isEqualTo(2);
        get(a, "/api/alerts").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].alertType").value("STOCK_AVAILABLE"))
                .andExpect(jsonPath("$.items[0].watchId").value(wa.get("id").asLong()));
        get(b, "/api/alerts").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].alertType").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.items[0].watchId").value(wb.get("id").asLong()));
    }

    @Test
    void newWatchOnExistingSourceUsesBaselineWithoutAlerting() throws Exception {
        Cookie a = register("late1@example.com");
        Cookie b = register("late2@example.com");
        var wa = createWatch(a, "{\"url\":\"demo://careers\",\"watchType\":\"ANY_CHANGE\"}");
        check(sourceIdOf(wa));
        var wb = createWatch(b, "{\"url\":\"demo://careers\",\"watchType\":\"KEYWORD_FOUND\",\"targetKeyword\":\"Product Designer\"}");
        assertThat(wb.get("conditionState").asText()).isEqualTo("Keyword is on the page");
        assertThat(wb.get("conditionMet").asBoolean()).isTrue();
        assertThat(wb.get("title").asText()).isEqualTo("Careers at Lumen Labs");
        check(sourceIdOf(wa));
        assertThat(alerts()).isZero();
    }

    @Test
    void keywordAppearAndRemove() throws Exception {
        Cookie u = register("k@example.com");
        Long src = sourceIdOf(createWatch(u, "{\"url\":\"demo://careers\",\"watchType\":\"KEYWORD_FOUND\",\"targetKeyword\":\"rust engineer\",\"cooldownMinutes\":0}"));
        createWatch(u, "{\"url\":\"demo://careers\",\"watchType\":\"KEYWORD_REMOVED\",\"targetKeyword\":\"Rust Engineer\",\"cooldownMinutes\":0}");
        check(src);
        demo.simulate("careers", DemoService.Action.ADD_KEYWORD, "Rust Engineer");
        assertThat(check(src).alertsCreated()).isEqualTo(1);
        demo.simulate("careers", DemoService.Action.REMOVE_KEYWORD, "Rust Engineer");
        assertThat(check(src).alertsCreated()).isEqualTo(1);
        assertThat(jdbc.queryForList("select alert_type from alert order by id", String.class))
                .containsExactly("KEYWORD_FOUND", "KEYWORD_REMOVED");
    }

    @Test
    void cooldownDefersAlertUntilItEnds() throws Exception {
        Cookie u = register("cd@example.com");
        var w = createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\",\"cooldownMinutes\":60}");
        Long src = sourceIdOf(w);
        check(src);
        demo.simulate("headphones", DemoService.Action.PRICE_DROP, null);
        assertThat(check(src).alertsCreated()).isEqualTo(1);
        demo.simulate("headphones", DemoService.Action.PRICE_DROP, null);
        assertThat(check(src).alertsCreated()).isZero(); // in cooldown
        jdbc.update("update watch set last_triggered_at = now() - interval '2 hours'");
        assertThat(check(src).alertsCreated()).isEqualTo(1); // deferred event delivered after cooldown
        assertThat(alerts()).isEqualTo(2);
    }

    @Test
    void oneTimeWatchCompletesAndPausedWatchesAreNotEvaluated() throws Exception {
        Cookie u = register("ot@example.com");
        var once = createWatch(u, "{\"url\":\"demo://console\",\"watchType\":\"PRICE_DROP\",\"alertMode\":\"ONE_TIME\",\"cooldownMinutes\":0}");
        var paused = createWatch(u, "{\"url\":\"demo://console\",\"watchType\":\"ANY_CHANGE\",\"cooldownMinutes\":0}");
        Long src = sourceIdOf(once);
        check(src);
        post(u, "/api/watches/" + paused.get("id").asLong() + "/pause", null).andExpect(status().isOk());
        demo.simulate("console", DemoService.Action.PRICE_DROP, null);
        assertThat(check(src).alertsCreated()).isEqualTo(1);
        get(u, "/api/watches/" + once.get("id").asLong()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        demo.simulate("console", DemoService.Action.PRICE_DROP, null);
        // no active watches left: source is not even claimed by the scheduler
        assertThat(monitoring.claimDue(10)).isEmpty();
        assertThat(alerts()).isEqualTo(1);
    }

    @Test
    void failedFetchesBackOffAndEscalate() throws Exception {
        Cookie u = register("f@example.com");
        Long src = sourceIdOf(createWatch(u, "{\"url\":\"demo://transit\",\"watchType\":\"ANY_CHANGE\"}"));
        check(src);
        for (int i = 0; i < 5; i++) demo.simulate("transit", DemoService.Action.FAIL_NEXT, null);

        var first = check(src);
        assertThat(first.outcome()).isEqualTo(CheckOutcome.FAILED);
        var row = jdbc.queryForMap("select status, failure_count, next_fetch_at, last_fetched_at from monitoring_source where id = ?", src);
        assertThat(row.get("status")).isEqualTo("DEGRADED");
        assertThat(row.get("failure_count")).isEqualTo(1);

        for (int i = 0; i < 4; i++) check(src);
        row = jdbc.queryForMap("select status, failure_count, next_fetch_at, last_fetched_at from monitoring_source where id = ?", src);
        assertThat(row.get("status")).isEqualTo("FAILING");
        assertThat(row.get("failure_count")).isEqualTo(5);
        long backoffMinutes = (((java.sql.Timestamp) row.get("next_fetch_at")).getTime() - ((java.sql.Timestamp) row.get("last_fetched_at")).getTime()) / 60000;
        assertThat(backoffMinutes).isEqualTo(16); // 1-minute demo interval * 2^4

        // users are told once that monitoring has a problem
        assertThat(jdbc.queryForList("select alert_type from alert", String.class)).containsExactly("MONITORING_ISSUE");
        get(u, "/api/monitoring/health").andExpect(jsonPath("$.failing").value(1))
                .andExpect(jsonPath("$.attention[0].status").value("FAILING"));
        get(u, "/api/monitoring/checks?filter=failures").andExpect(jsonPath("$.totalItems").value(5));

        // recovery resets the failure state
        assertThat(check(src).outcome()).isEqualTo(CheckOutcome.UNCHANGED);
        assertThat(jdbc.queryForObject("select failure_count from monitoring_source where id = ?", Integer.class, src)).isZero();
    }

    @Test
    void claimingIsExclusiveAcrossConcurrentWorkers() throws Exception {
        Cookie u = register("cc@example.com");
        for (String slug : List.of("headphones", "sneakers", "console", "careers", "transit")) {
            createWatch(u, "{\"url\":\"demo://" + slug + "\",\"watchType\":\"ANY_CHANGE\"}");
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return monitoring.claimDue(5);
            }));
        }
        start.countDown();
        List<Long> all = new ArrayList<>();
        for (var f : futures) all.addAll(f.get(10, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(all).hasSize(5).doesNotHaveDuplicates();
        // already leased: neither claims nor manual checks can grab them
        assertThat(monitoring.claimDue(5)).isEmpty();
        assertThat(monitoring.checkNow(all.get(0))).isEmpty();
        // completing the run releases the lease
        monitoring.runLeased(all.get(0));
        assertThat(monitoring.checkNow(all.get(0))).isPresent();
    }

    @Test
    void concurrentManualChecksRunOnce() throws Exception {
        Cookie u = register("cm@example.com");
        Long src = sourceIdOf(createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\"}"));
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<MonitoringService.CheckSummary>>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) results.add(pool.submit(() -> { start.await(); return monitoring.checkNow(src); }));
        start.countDown();
        int ran = 0;
        for (var f : results) if (f.get(20, TimeUnit.SECONDS).isPresent()) ran++;
        pool.shutdown();
        assertThat(ran).isGreaterThanOrEqualTo(1);
        // Every run that did happen was serialized; at most one baseline exists
        assertThat(jdbc.queryForObject("select count(*) from snapshot", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from check_run", Integer.class)).isEqualTo(ran);
    }

    @Test
    void schedulerProcessesDueSourcesAutomatically() throws Exception {
        Cookie u = register("sch@example.com");
        Long src = sourceIdOf(createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_DROP\"}"));
        MonitoringScheduler scheduler = new MonitoringScheduler(monitoring, executor, props, Clock.systemUTC());
        scheduler.tick();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && jdbc.queryForObject("select count(*) from check_run", Integer.class) == 0) {
            Thread.sleep(100);
        }
        assertThat(jdbc.queryForObject("select outcome from check_run where source_id = ?", String.class, src)).isEqualTo("BASELINE");
        var next = jdbc.queryForObject("select next_fetch_at from monitoring_source", java.sql.Timestamp.class).toInstant();
        assertThat(next).isAfter(Instant.now().plusSeconds(50));
        // not due any more
        scheduler.tick();
        Thread.sleep(300);
        assertThat(jdbc.queryForObject("select count(*) from check_run", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select locked_by from monitoring_source", String.class)).isNull();
    }

    @Test
    void checkNowEndpointAndDemoSimulation() throws Exception {
        Cookie u = register("cn@example.com");
        Cookie stranger = register("st@example.com");
        long id = createWatch(u, "{\"url\":\"demo://headphones\",\"watchType\":\"PRICE_BELOW\",\"targetPrice\":180,\"cooldownMinutes\":0}").get("id").asLong();
        post(u, "/api/watches/" + id + "/check-now", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ran").value(true)).andExpect(jsonPath("$.outcome").value("BASELINE"));
        // a stranger's simulation only updates the page; it doesn't run a check on someone else's source
        post(stranger, "/api/demo/sources/headphones/simulate", "{\"action\":\"PRICE_DROP\"}")
                .andExpect(jsonPath("$.checked").value(false));
        post(u, "/api/demo/sources/headphones/simulate", "{\"action\":\"RESET\"}")
                .andExpect(jsonPath("$.checked").value(true)).andExpect(jsonPath("$.alertsCreated").value(0));
        post(u, "/api/demo/sources/headphones/simulate", "{\"action\":\"PRICE_DROP\"}")
                .andExpect(jsonPath("$.checked").value(true)).andExpect(jsonPath("$.alertsCreated").value(1));
        get(u, "/api/alerts/unread-count").andExpect(jsonPath("$.unread").value(1));
        get(u, "/api/dashboard").andExpect(jsonPath("$.activeWatches").value(1)).andExpect(jsonPath("$.unreadAlerts").value(1))
                .andExpect(jsonPath("$.recentAlerts[0].alertType").value("PRICE_BELOW"));
    }
}
