package com.example.backend.task.dto;

import com.example.backend.task.entity.TaskPriority;
import com.example.backend.task.entity.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long projectId,
        String projectName,
        Long ownerId,
        String ownerName,
        Long assigneeId,
        String assigneeName,
        String assigneeEmail,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        boolean manageable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
