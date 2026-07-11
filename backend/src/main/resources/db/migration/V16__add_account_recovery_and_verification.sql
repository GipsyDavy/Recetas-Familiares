ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verified_at timestamptz NULL;

CREATE TABLE account_action_tokens (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token_type VARCHAR(40) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_action_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_action_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_account_action_tokens_user_type
    ON account_action_tokens (user_id, token_type, consumed_at, expires_at);

CREATE INDEX ix_account_action_tokens_cleanup
    ON account_action_tokens (expires_at, consumed_at);
