CREATE TABLE chat_messages (
    id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    author_user_id CHAR(36) NOT NULL,
    body VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_chat_messages_author FOREIGN KEY (author_user_id) REFERENCES users (id)
);

CREATE INDEX ix_chat_messages_family_cursor ON chat_messages (family_id, created_at, id);
CREATE INDEX ix_chat_messages_author ON chat_messages (author_user_id);

-- Borrado/limpieza por usuario: cada miembro puede ocultar su propia vista del
-- historial sin afectar a los demas. cleared_before marca el corte: solo se
-- muestran mensajes con created_at estrictamente posterior.
CREATE TABLE chat_message_clears (
    id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    cleared_before TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_chat_message_clears_family_user UNIQUE (family_id, user_id),
    CONSTRAINT fk_chat_message_clears_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_chat_message_clears_user FOREIGN KEY (user_id) REFERENCES users (id)
);
