package com.example.backend.auth.dto;

import com.example.backend.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
        @Pattern(
                regexp = ValidationPatterns.PASSWORD,
                message = "Password must contain upper, lower, digit and special character without spaces"
        )
        String newPassword
) {
}
