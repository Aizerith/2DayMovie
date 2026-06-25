ALTER TABLE watch_room
    ADD COLUMN IF NOT EXISTS playback_video_object_key VARCHAR(500) UNIQUE,
    ADD COLUMN IF NOT EXISTS playback_video_content_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS playback_video_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS playback_video_etag VARCHAR(255);
