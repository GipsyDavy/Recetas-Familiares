CREATE TABLE recipes (
    id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NULL,
    servings INT NULL,
    prep_minutes INT NULL,
    cook_minutes INT NULL,
    difficulty VARCHAR(40) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recipes_family FOREIGN KEY (family_id) REFERENCES families (id)
);

CREATE INDEX ix_recipes_family_active ON recipes (family_id, deleted, updated_at);
CREATE INDEX ix_recipes_sync ON recipes (updated_at, sync_version, deleted);
