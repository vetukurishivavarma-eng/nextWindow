package com.nextwindow.notification;

import com.nextwindow.auth.AuthUser;
import com.nextwindow.common.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/push")
public class PushController {
    private final PushService service;
    private final RateLimiter rateLimiter;

    public PushController(PushService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/subscriptions")
    public List<PushDtos.SubscriptionResponse> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.id());
    }

    @PostMapping("/subscribe")
    public PushDtos.SubscriptionResponse subscribe(@AuthenticationPrincipal AuthUser user,
                                                   @Valid @RequestBody PushDtos.SubscribeRequest req,
                                                   @RequestHeader(value = "User-Agent", required = false) String ua) {
        rateLimiter.check(RateLimiter.Policy.SENSITIVE, "push:" + user.id());
        return service.subscribe(user.id(), req, ua);
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PushDtos.UnsubscribeRequest req) {
        service.unsubscribe(user.id(), req.endpoint());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.remove(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public PushDtos.TestResult test(@AuthenticationPrincipal AuthUser user) {
        rateLimiter.check(RateLimiter.Policy.PUSH_TEST, "user:" + user.id());
        return service.sendTest(user.id());
    }
}
