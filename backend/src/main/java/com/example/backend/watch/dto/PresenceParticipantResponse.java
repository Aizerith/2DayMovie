package com.example.backend.watch.dto;

public record PresenceParticipantResponse(
        String clientId,
        String displayName,
        String avatar,
        long joinedAt
) {
}
