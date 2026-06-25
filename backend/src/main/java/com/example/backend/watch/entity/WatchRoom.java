package com.example.backend.watch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "watch_room")
@Getter
@Setter
public class WatchRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_code", nullable = false, unique = true, length = 16)
    private String shareCode;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(name = "video_original_filename", nullable = false, length = 255)
    private String videoOriginalFilename;

    @Column(name = "video_content_type", nullable = false, length = 255)
    private String videoContentType;

    @Column(name = "video_object_key", nullable = false, unique = true, length = 500)
    private String videoObjectKey;

    @Column(name = "video_size_bytes")
    private Long videoSizeBytes;

    @Column(name = "video_etag", length = 255)
    private String videoEtag;

    @Column(name = "playback_video_object_key", unique = true, length = 500)
    private String playbackVideoObjectKey;

    @Column(name = "playback_video_content_type", length = 255)
    private String playbackVideoContentType;

    @Column(name = "playback_video_size_bytes")
    private Long playbackVideoSizeBytes;

    @Column(name = "playback_video_etag", length = 255)
    private String playbackVideoEtag;

    @Column(name = "subtitle_original_filename", length = 255)
    private String subtitleOriginalFilename;

    @Column(name = "subtitle_content_type", length = 255)
    private String subtitleContentType;

    @Column(name = "subtitle_object_key", unique = true, length = 500)
    private String subtitleObjectKey;

    @Column(name = "subtitle_size_bytes")
    private Long subtitleSizeBytes;

    @Column(name = "subtitle_etag", length = 255)
    private String subtitleEtag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WatchRoomStatus status;

    @Column(name = "playback_time_seconds", nullable = false)
    private double playbackTimeSeconds;

    @Column(nullable = false)
    private boolean playing;

    @Column(name = "playback_updated_at", nullable = false)
    private LocalDateTime playbackUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        playbackUpdatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
