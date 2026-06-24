CREATE TABLE app_user (
                          id BIGSERIAL PRIMARY KEY,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          first_name VARCHAR(100) NOT NULL,
                          last_name VARCHAR(100) NOT NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);