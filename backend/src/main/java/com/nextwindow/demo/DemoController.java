package com.nextwindow.demo;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.common.RateLimiter;
import com.nextwindow.monitoring.MonitoringService;
import com.nextwindow.source.MonitoringSourceRepository;
import com.nextwindow.watch.WatchRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Development-only controls for deterministic demo sources. Not registered unless demo mode is enabled. */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(prefix = "nextwindow.demo", name = "enabled", havingValue = "true")
public class DemoController {
    public record DemoSourceResponse(String url, String slug, String title, BigDecimal price, String currency,
                                     String availability, String description, int pendingFailures) {}

    public record SimulateRequest(@NotNull DemoService.Action action, @Size(max = 100) String keyword, Boolean checkNow) {}

    public record SimulateResponse(DemoSourceResponse source, boolean checked, String outcome, int alertsCreated, String message) {}

    private final DemoService demo;
    private final MonitoringService monitoring;
    private final MonitoringSourceRepository sources;
    private final WatchRepository watches;
    private final RateLimiter rateLimiter;

    public DemoController(DemoService demo, MonitoringService monitoring, MonitoringSourceRepository sources,
                          WatchRepository watches, RateLimiter rateLimiter) {
        this.demo = demo;
        this.monitoring = monitoring;
        this.sources = sources;
        this.watches = watches;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/sources")
    public List<DemoSourceResponse> list() {
        return demo.all().stream().map(DemoController::dto).toList();
    }

    @PostMapping("/sources/{slug}/simulate")
    public SimulateResponse simulate(@AuthenticationPrincipal AuthUser user, @PathVariable String slug,
                                     @Valid @RequestBody SimulateRequest req) {
        rateLimiter.check(RateLimiter.Policy.DEMO, "user:" + user.id());
        DemoPage page = demo.simulate(slug, req.action(), req.keyword());
        var source = sources.findByCanonicalUrl("demo://" + slug);
        // Only run a check if the caller watches this source, so the shortcut can't be used to probe others.
        if (Boolean.FALSE.equals(req.checkNow()) || source.isEmpty()
                || !watches.existsBySourceIdAndUserId(source.get().getId(), user.id())) {
            return new SimulateResponse(dto(page), false, null, 0,
                    "Demo page updated. The next scheduled check will pick up the change.");
        }
        return monitoring.checkNow(source.get().getId())
                .map(r -> new SimulateResponse(dto(page), true, r.outcome().name(), r.alertsCreated(), r.message()))
                .orElseGet(() -> new SimulateResponse(dto(page), false, null, 0, "A check is already running."));
    }

    static DemoSourceResponse dto(DemoPage p) {
        DemoCatalog.Entry e = DemoCatalog.find(p.getSlug());
        return new DemoSourceResponse("demo://" + p.getSlug(), p.getSlug(), p.getTitle(), p.getPrice(), p.getCurrency(),
                p.getAvailability().name(), e == null ? null : e.description(), p.getFailNext());
    }
}
