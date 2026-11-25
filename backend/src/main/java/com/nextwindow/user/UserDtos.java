package com.nextwindow.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record UserResponse(Long id, String email, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getCreatedAt());
        }
    }

    public record SettingsResponse(String email, boolean pushEnabled, boolean pushConfigured, int pushDevices) {}

    public record UpdateSettingsRequest(@NotNull Boolean pushEnabled) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128, message = "Password must be 8–128 characters") String newPassword) {}

    public record DeleteAccountRequest(@NotBlank String password) {}
}
