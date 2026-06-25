package com.example.backend.watch.dto;

import java.util.List;

public record WatchRoomAccessResponse(
        String shareCode,
        String title,
        String videoUrl,
        String subtitleUrl,
        List<SubtitleTrackResponse> subtitleTracks,
        List<AudioTrackResponse> audioTracks,
        String videoContentType,
        double playbackTimeSeconds,
        boolean playing
) {
}
