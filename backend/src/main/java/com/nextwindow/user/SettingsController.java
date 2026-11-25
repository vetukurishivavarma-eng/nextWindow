package com.nextwindow.user;

import com.nextwindow.auth.AuthService;
import com.nextwindow.auth.AuthUser;
import com.nextwindow.auth.SessionService;
import com.nextwindow.common.ApiException;
import com.nextwindow.common.RateLimiter;
import com.nextwindow.notification.PushService;
import com.nextwindow.notification.PushSubscriptionRepository;
import com.nextwindow.source.MonitoringSourceRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class SettingsController {
    private final UserRepository users;
    private final PushService push;
    private final PushSubscriptionRepository subscriptions;
    private final AuthService auth;
    private final SessionService sessions;
    private final RateLimiter rateLimiter;
    private final EntityManager em;

    public SettingsController(UserRepository users, PushService push, PushSubscriptionRepository subscriptions,
                              AuthService auth, SessionService sessions, RateLimiter rateLimiter, EntityManager em) {
        this.users = users;
        this.push = push;
        this.subscriptions = subscriptions;
        this.auth = auth;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.em = em;
    }

    @GetMapping("/api/settings")
    public UserDtos.SettingsResponse get(@AuthenticationPrincipal AuthUser principal) {
        User u = users.findById(principal.id()).orElseThrow(() -> ApiException.notFound("User"));
        return new UserDtos.SettingsResponse(u.getEmail(), u.isPushEnabled(), push.isConfigured(),
                (int) subscriptions.countByUserId(u.getId()));
    }

    @PutMapping("/api/settings")
    @Transactional
    public UserDtos.SettingsResponse update(@AuthenticationPrincipal AuthUser principal, @Valid @RequestBody UserDtos.UpdateSettingsRequest req) {
        User u = users.findById(principal.id()).orElseThrow(() -> ApiException.notFound("User"));
        u.setPushEnabled(req.pushEnabled());
        return new UserDtos.SettingsResponse(u.getEmail(), u.isPushEnabled(), push.isConfigured(),
                (int) subscriptions.countByUserId(u.getId()));
    }

    @PostMapping("/api/account/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser principal, @Valid @RequestBody UserDtos.ChangePasswordRequest req) {
        rateLimiter.check(RateLimiter.Policy.SENSITIVE, "pw:" + principal.id());
        auth.changePassword(principal.id(), req.currentPassword(), req.newPassword());
        sessions.revokeOthers(principal.id(), principal.sessionId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/account")
    @Transactional
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthUser principal, @Valid @RequestBody UserDtos.DeleteAccountRequest req) {
        rateLimiter.check(RateLimiter.Policy.SENSITIVE, "del:" + principal.id());
        auth.verifyPassword(principal.id(), req.password());
        users.deleteById(principal.id()); // cascades sessions, watches, alerts, push subscriptions
        users.flush();
        em.createNativeQuery("DELETE FROM monitoring_source s WHERE NOT EXISTS (SELECT 1 FROM watch w WHERE w.source_id = s.id) "
                + "AND s.created_at < now() - interval '1 hour'").executeUpdate();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearCookie().toString()).build();
    }
}
