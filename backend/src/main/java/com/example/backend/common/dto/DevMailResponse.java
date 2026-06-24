package com.example.backend.common.dto;

import java.time.LocalDateTime;

public record DevMailResponse(
        long id,
        String to,
        String subject,
        String body,
        LocalDateTime createdAt
) {
}
