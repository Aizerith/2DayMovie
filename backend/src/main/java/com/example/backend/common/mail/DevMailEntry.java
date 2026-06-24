package com.example.backend.common.mail;

import java.time.LocalDateTime;

public record DevMailEntry(
        long id,
        String to,
        String subject,
        String body,
        LocalDateTime createdAt
) {
}
