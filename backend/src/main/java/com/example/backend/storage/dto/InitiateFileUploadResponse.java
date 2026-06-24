package com.example.backend.storage.dto;

import java.time.LocalDateTime;

public record InitiateFileUploadResponse(
        Long fileId,
        String uploadUrl,
        LocalDateTime expiresAt
) {
}
