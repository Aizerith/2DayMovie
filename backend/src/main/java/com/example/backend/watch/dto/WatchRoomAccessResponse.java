package com.example.backend.watch.dto;

public record WatchRoomAccessResponse(
        String shareCode,
        String title,
        String videoUrl,
        String subtitleUrl,
        String videoContentType,
        double playbackTimeSeconds,
        boolean playing
) {
}
