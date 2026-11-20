CREATE TABLE attachments (
    id uuid PRIMARY KEY,
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    uploader_id uuid NOT NULL REFERENCES app_users(id),
    filename varchar(180) NOT NULL,
    storage_key varchar(36) NOT NULL UNIQUE,
    content_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_attachments_task ON attachments(task_id, created_at);
