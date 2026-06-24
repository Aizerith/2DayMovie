package com.example.backend.watch.dto;

public record PresignedUpload(
        String url,
        String objectKey
) {
}
