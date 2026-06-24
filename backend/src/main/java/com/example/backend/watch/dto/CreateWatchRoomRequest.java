package com.example.backend.watch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWatchRoomRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Pattern(regexp = "\\d{4}") String pin,
        @Valid @NotNull UploadAssetRequest video,
        @Valid UploadAssetRequest subtitle
) {
}
