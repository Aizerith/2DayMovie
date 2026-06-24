package com.example.backend.project.dto;

import com.example.backend.project.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 1000) String description,
        @NotNull ProjectStatus status
) {
}
