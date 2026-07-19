-- Conversacion privada 1:1 entre dos miembros de una misma familia. El par de
-- usuarios va normalizado (user_a_id < user_b_id lexicograficamente) para que
-- exista como maximo una conversacion por par y familia.
CREATE TABLE private_conversations (
    id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    user_a_id VARCHAR(36) NOT NULL,
    user_b_id VARCHAR(36) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_private_conversations_pair UNIQUE (family_id, user_a_id, user_b_id),
    CONSTRAINT fk_private_conversations_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_private_conversations_user_a FOREIGN KEY (user_a_id) REFERENCES users (id),
    CONSTRAINT fk_private_conversations_user_b FOREIGN KEY (user_b_id) REFERENCES users (id)
);

CREATE INDEX ix_private_conversations_user_a ON private_conversations (user_a_id);
CREATE INDEX ix_private_conversations_user_b ON private_conversations (user_b_id);

CREATE TABLE private_messages (
    id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    author_user_id VARCHAR(36) NOT NULL,
    body VARCHAR(2000) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_private_messages_conversation FOREIGN KEY (conversation_id) REFERENCES private_conversations (id),
    CONSTRAINT fk_private_messages_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);

CREATE INDEX ix_private_messages_conversation_cursor ON private_messages (conversation_id, created_at, id);
CREATE INDEX ix_private_messages_author ON private_messages (author_user_id);

CREATE TABLE private_message_attachments (
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
    CONSTRAINT fk_private_message_attachments_message FOREIGN KEY (message_id) REFERENCES private_messages (id)
);

-- Borrado/limpieza por usuario, igual que chat_message_clears pero por
-- conversacion en vez de por familia: cada participante puede ocultar su
-- propia vista del historial sin afectar al otro.
CREATE TABLE private_message_clears (
    id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    cleared_before timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT uq_private_message_clears_conversation_user UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_private_message_clears_conversation FOREIGN KEY (conversation_id) REFERENCES private_conversations (id),
    CONSTRAINT fk_private_message_clears_user FOREIGN KEY (user_id) REFERENCES users (id)
);
