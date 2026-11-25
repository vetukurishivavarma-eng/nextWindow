package com.nextwindow.watch;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.common.PageResponse;
import com.nextwindow.common.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/watches")
public class WatchController {
    private final WatchService service;
    private final RateLimiter rateLimiter;

    public WatchController(WatchService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<WatchDtos.WatchResponse> create(@AuthenticationPrincipal AuthUser user,
                                                          @Valid @RequestBody WatchDtos.CreateWatchRequest req) {
        rateLimiter.check(RateLimiter.Policy.WATCH_CREATE, "user:" + user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.id(), req));
    }

    @GetMapping
    public PageResponse<WatchDtos.WatchResponse> list(@AuthenticationPrincipal AuthUser user,
                                                      @RequestParam(defaultValue = "all") String filter,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(user.id(), filter, q, page, size);
    }

    @GetMapping("/{id}")
    public WatchDtos.WatchResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public WatchDtos.WatchResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody WatchDtos.UpdateWatchRequest req) {
        return service.update(user.id(), id, req);
    }

    @PostMapping("/{id}/pause")
    public WatchDtos.WatchResponse pause(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.setActive(user.id(), id, false);
    }

    @PostMapping("/{id}/resume")
    public WatchDtos.WatchResponse resume(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.setActive(user.id(), id, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/check-now")
    public WatchDtos.CheckNowResponse checkNow(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        rateLimiter.check(RateLimiter.Policy.MANUAL_CHECK, "user:" + user.id());
        return service.checkNow(user.id(), id);
    }

    @GetMapping("/{id}/snapshots")
    public PageResponse<WatchDtos.SnapshotResponse> snapshots(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size) {
        return service.snapshots(user.id(), id, page, size);
    }

    @GetMapping("/{id}/checks")
    public PageResponse<WatchDtos.CheckRunResponse> checks(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return service.checks(user.id(), id, page, size);
    }
}
