CREATE TABLE stored_file (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    bucket_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    size_bytes BIGINT,
    etag VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stored_file_owner_updated_at
    ON stored_file (owner_id, updated_at DESC);
