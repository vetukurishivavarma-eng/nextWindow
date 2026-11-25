package com.nextwindow.auth;

import com.nextwindow.common.ApiException;
import com.nextwindow.user.User;
import com.nextwindow.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {
    // Hash of a random value; checked when the user does not exist so login timing does not reveal accounts.
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public User register(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        validatePasswordStrength(password);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }
        try {
            return users.saveAndFlush(new User(email, encoder.encode(password), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }
    }

    @Transactional(readOnly = true)
    public User login(String rawEmail, String password) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(rawEmail)).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!encoder.matches(password, user.getPasswordHash()) || !user.isEnabled()) {
            throw invalidCredentials();
        }
        return user;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Your current password is incorrect.");
        }
        validatePasswordStrength(newPassword);
        user.setPasswordHash(encoder.encode(newPassword));
    }

    @Transactional(readOnly = true)
    public void verifyPassword(Long userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Password is incorrect.");
        }
    }

    static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least 8 characters.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean other = password.chars().anyMatch(c -> !Character.isLetter(c));
        if (!letter || !other) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must contain letters and at least one number or symbol.");
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
}
