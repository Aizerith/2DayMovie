package com.example.backend.common.exception;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String message
) {
}
