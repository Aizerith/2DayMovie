package com.example.backend.auth.dto;

import com.example.backend.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "Must be a valid email address")
        String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank
        @Pattern(
                regexp = ValidationPatterns.PASSWORD,
                message = "Password must contain upper, lower, digit and special character without spaces"
        )
        String password
) {
}
