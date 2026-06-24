package com.example.backend.storage.dto;

import java.time.LocalDateTime;

public record PresignedDownloadResponse(
        String fileName,
        String downloadUrl,
        LocalDateTime expiresAt
) {
}
