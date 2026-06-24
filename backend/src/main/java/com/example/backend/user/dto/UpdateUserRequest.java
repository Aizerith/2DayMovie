package com.example.backend.user.dto;

import com.example.backend.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRequest(
        @NotBlank
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "Must be a valid email address")
        String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String password,
        @NotBlank String role,
        @NotNull Boolean enabled
) {
}
