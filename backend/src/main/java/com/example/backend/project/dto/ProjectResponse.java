package com.example.backend.project.dto;

import com.example.backend.project.entity.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
