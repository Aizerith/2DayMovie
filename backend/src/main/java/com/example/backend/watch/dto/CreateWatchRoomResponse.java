package com.example.backend.watch.dto;

import java.time.LocalDateTime;

public record CreateWatchRoomResponse(
        String shareCode,
        String shareUrl,
        PresignedUpload videoUpload,
        PresignedUpload subtitleUpload,
        LocalDateTime expiresAt
) {
}
