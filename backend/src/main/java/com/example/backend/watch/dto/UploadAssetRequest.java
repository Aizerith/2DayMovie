package com.example.backend.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadAssetRequest(
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 255) String contentType,
        @Positive long sizeBytes
) {
}
