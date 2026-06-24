package com.example.backend.auth.dto;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean emailVerified
) {
}
