package com.example.backend.auth.dto;

import com.example.backend.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForgotPasswordRequest(
        @NotBlank
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "Must be a valid email address")
        String email
) {
}
