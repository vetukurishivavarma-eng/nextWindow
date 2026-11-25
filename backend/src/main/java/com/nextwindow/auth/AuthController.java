package com.nextwindow.auth;

import com.nextwindow.common.ApiException;
import com.nextwindow.common.RateLimiter;
import com.nextwindow.user.User;
import com.nextwindow.user.UserDtos.UserResponse;
import com.nextwindow.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;
    private final RateLimiter rateLimiter;
    private final UserRepository users;

    public AuthController(AuthService authService, SessionService sessionService, RateLimiter rateLimiter, UserRepository users) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req, HttpServletRequest http) {
        rateLimiter.check(RateLimiter.Policy.REGISTER, ClientIp.of(http));
        User user = authService.register(req.email(), req.password());
        String token = sessionService.createSession(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionService.sessionCookie(token).toString())
                .body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
        rateLimiter.check(RateLimiter.Policy.LOGIN, ClientIp.of(http));
        rateLimiter.check(RateLimiter.Policy.LOGIN, "email:" + AuthService.normalizeEmail(req.email()));
        User user = authService.login(req.email(), req.password());
        String token = sessionService.createSession(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionService.sessionCookie(token).toString())
                .body(UserResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        sessionService.revoke(readToken(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionService.clearCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in.");
        }
        return users.findById(principal.id()).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in."));
    }

    private String readToken(HttpServletRequest http) {
        if (http.getCookies() == null) return null;
        for (Cookie c : http.getCookies()) {
            if (c.getName().equals(sessionService.cookieName())) return c.getValue();
        }
        return null;
    }
}
