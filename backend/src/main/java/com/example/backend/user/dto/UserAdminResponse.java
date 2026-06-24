package com.example.backend.user.dto;

public record UserAdminResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean enabled,
        boolean emailVerified
) {
}
