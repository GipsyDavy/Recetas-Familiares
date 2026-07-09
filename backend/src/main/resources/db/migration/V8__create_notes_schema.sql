CREATE TABLE family_notes (
    id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    recipe_id VARCHAR(36) NULL,
    title VARCHAR(180) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_family_notes_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_family_notes_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);

CREATE INDEX ix_family_notes_family_active ON family_notes (family_id, deleted, pinned, updated_at);
CREATE INDEX ix_family_notes_recipe ON family_notes (recipe_id);
CREATE INDEX ix_family_notes_sync ON family_notes (updated_at, sync_version, deleted);
