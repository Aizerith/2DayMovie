package com.example.backend.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlaybackSyncMessage(
        @NotBlank @Pattern(regexp = "\\d{4}") String pin,
        @NotBlank String clientId,
        double currentTime,
        boolean playing,
        String event,
        long sentAt
) {
}
