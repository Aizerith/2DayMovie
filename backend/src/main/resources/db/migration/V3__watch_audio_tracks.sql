CREATE TABLE IF NOT EXISTS watch_audio_track (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES watch_room (id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    language VARCHAR(16) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_watch_audio_track_room_order
    ON watch_audio_track (room_id, display_order);
