package com.nextwindow.source;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.common.ApiException;
import com.nextwindow.common.RateLimiter;
import com.nextwindow.watch.WatchDtos;
import com.nextwindow.watch.WatchMapper;
import com.nextwindow.watch.WatchRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sources")
public class SourceController {
    public record AnalyzeRequest(@NotBlank(message = "Enter a URL") @Size(max = 2048) String url) {}

    private final SourceAnalysisService analysis;
    private final MonitoringSourceRepository sources;
    private final WatchRepository watches;
    private final RateLimiter rateLimiter;

    public SourceController(SourceAnalysisService analysis, MonitoringSourceRepository sources, WatchRepository watches,
                            RateLimiter rateLimiter) {
        this.analysis = analysis;
        this.sources = sources;
        this.watches = watches;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/analyze")
    public SourceAnalysisService.AnalysisResponse analyze(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AnalyzeRequest req) {
        rateLimiter.check(RateLimiter.Policy.SOURCE_ANALYZE, "user:" + user.id());
        return analysis.analyze(req.url());
    }

    /** Monitoring status of a source; only visible to users who watch it. */
    @GetMapping("/{id}")
    public WatchDtos.SourceSummary status(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        if (!watches.existsBySourceIdAndUserId(id, user.id())) throw ApiException.notFound("Source");
        return WatchMapper.source(sources.findById(id).orElseThrow(() -> ApiException.notFound("Source")));
    }
}
