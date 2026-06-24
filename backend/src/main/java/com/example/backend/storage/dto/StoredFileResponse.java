package com.example.backend.storage.dto;

import com.example.backend.storage.entity.StoredFileStatus;
import java.time.LocalDateTime;

public record StoredFileResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        StoredFileStatus status,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        LocalDateTime uploadedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
