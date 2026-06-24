package com.example.backend.task.dto;

import com.example.backend.task.entity.TaskPriority;
import com.example.backend.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
        @NotNull Long projectId,
        Long assigneeId,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotNull TaskStatus status,
        @NotNull TaskPriority priority,
        LocalDate dueDate
) {
}
