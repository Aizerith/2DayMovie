package com.example.backend.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record PresenceMessage(
        @Pattern(regexp = "\\d{4}") String pin,
        @NotBlank String clientId,
        String displayName,
        String avatar,
        String event,
        List<PresenceParticipantResponse> participants,
        long sentAt
) {
}
