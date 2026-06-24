package com.example.backend.user.dto;

public record AssignableUserResponse(
        Long id,
        String email,
        String fullName,
        String role
) {
}
