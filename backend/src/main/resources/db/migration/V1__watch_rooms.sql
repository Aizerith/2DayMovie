CREATE TABLE watch_room (
    id BIGSERIAL PRIMARY KEY,
    share_code VARCHAR(16) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    pin_hash VARCHAR(100) NOT NULL,
    video_original_filename VARCHAR(255) NOT NULL,
    video_content_type VARCHAR(255) NOT NULL,
    video_object_key VARCHAR(500) NOT NULL UNIQUE,
    video_size_bytes BIGINT,
    video_etag VARCHAR(255),
    subtitle_original_filename VARCHAR(255),
    subtitle_content_type VARCHAR(255),
    subtitle_object_key VARCHAR(500) UNIQUE,
    subtitle_size_bytes BIGINT,
    subtitle_etag VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    playback_time_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    playing BOOLEAN NOT NULL DEFAULT FALSE,
    playback_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_watch_room_share_code
    ON watch_room (share_code);
