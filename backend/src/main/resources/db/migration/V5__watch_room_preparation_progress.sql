ALTER TABLE watch_room
    ADD COLUMN IF NOT EXISTS preparation_progress_percent INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS preparation_message VARCHAR(160) NOT NULL DEFAULT 'En attente';
