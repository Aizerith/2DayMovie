package com.example.backend.watch.dto;

public record CloseWatchRoomResponse(
        String shareCode,
        boolean closed
) {
}
