CREATE TABLE chat_attachments (
    id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    thumbnail_url VARCHAR(1024) NULL,
    storage_path VARCHAR(512) NOT NULL,
    thumbnail_storage_path VARCHAR(512) NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT NULL,
    height INT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_attachments_message FOREIGN KEY (message_id) REFERENCES chat_messages (id)
);

CREATE INDEX ix_chat_attachments_message ON chat_attachments (message_id);
CREATE INDEX ix_chat_attachments_storage_path ON chat_attachments (storage_path);
CREATE INDEX ix_chat_attachments_thumbnail_storage_path ON chat_attachments (thumbnail_storage_path);
