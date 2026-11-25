package com.nextwindow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 254) String email,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 128, message = "Password must be 8–128 characters") String password) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Size(max = 254) String email,
            @NotBlank(message = "Password is required") @Size(max = 128) String password) {}
}
