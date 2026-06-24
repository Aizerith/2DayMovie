package com.example.backend.common.mail;

public record MailMessage(
        String to,
        String subject,
        String body
) {
}
