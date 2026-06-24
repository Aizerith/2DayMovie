ALTER TABLE task
    ADD COLUMN assignee_id BIGINT REFERENCES app_user (id) ON DELETE SET NULL;

CREATE INDEX idx_task_assignee_id ON task (assignee_id);
