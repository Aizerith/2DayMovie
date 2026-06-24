package com.example.backend.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccessWatchRoomRequest(
        @NotBlank @Pattern(regexp = "\\d{4}") String pin
) {
}
