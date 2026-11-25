package com.nextwindow.auth;

import com.nextwindow.common.Hashing;
import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.user.User;
import com.nextwindow.user.UserRepository;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Issues and validates opaque session tokens. Tokens are random 256-bit values; only their hash is persisted. */
@Service
public class SessionService {
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final NextWindowProperties.Auth props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionService(AuthSessionRepository sessions, UserRepository users, NextWindowProperties properties, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.props = properties.auth();
        this.clock = clock;
    }

    /** Creates a session and returns the raw token (shown to the client once, via cookie). */
    @Transactional
    public String createSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        sessions.save(new AuthSession(user.getId(), Hashing.sha256Hex(token), now, now.plus(props.sessionTtl())));
        List<AuthSession> existing = sessions.findByUserIdOrderByLastUsedAtDesc(user.getId());
        if (existing.size() > props.maxSessionsPerUser()) {
            sessions.deleteAll(existing.subList(props.maxSessionsPerUser(), existing.size()));
        }
        return token;
    }

    /** Resolves a token to a principal, sliding its expiry. */
    @Transactional
    public Optional<AuthUser> authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByTokenHash(Hashing.sha256Hex(token))
                .filter(s -> s.getExpiresAt().isAfter(now))
                .flatMap(s -> users.findById(s.getUserId())
                        .filter(User::isEnabled)
                        .map(u -> {
                            if (Duration.between(s.getLastUsedAt(), now).compareTo(TOUCH_INTERVAL) > 0) {
                                s.setLastUsedAt(now);
                                s.setExpiresAt(now.plus(props.sessionTtl()));
                            }
                            return new AuthUser(u.getId(), u.getEmail(), s.getId());
                        }));
    }

    @Transactional
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            sessions.findByTokenHash(Hashing.sha256Hex(token)).ifPresent(sessions::delete);
        }
    }

    @Transactional
    public void revokeOthers(Long userId, Long keepSessionId) {
        sessions.deleteOtherSessions(userId, keepSessionId);
    }

    @Transactional
    public int purgeExpired() {
        return sessions.deleteExpired(clock.instant());
    }

    public ResponseCookie sessionCookie(String token) {
        return baseCookie(token).maxAge(props.sessionTtl()).build();
    }

    public ResponseCookie clearCookie() {
        return baseCookie("").maxAge(0).build();
    }

    public String cookieName() {
        return props.cookieName();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(props.cookieName(), value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite(props.cookieSameSite())
                .path("/");
    }
}
