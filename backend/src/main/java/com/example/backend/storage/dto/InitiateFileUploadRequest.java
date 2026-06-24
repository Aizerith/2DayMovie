package com.example.backend.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitiateFileUploadRequest(
        @NotBlank String originalFilename,
        String contentType,
        @NotNull @Positive Long sizeBytes
) {
}
