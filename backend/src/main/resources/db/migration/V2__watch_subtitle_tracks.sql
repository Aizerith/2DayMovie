CREATE TABLE IF NOT EXISTS watch_subtitle_track (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES watch_room (id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    language VARCHAR(16) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    source VARCHAR(40) NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_watch_subtitle_track_room_order
    ON watch_subtitle_track (room_id, display_order);
