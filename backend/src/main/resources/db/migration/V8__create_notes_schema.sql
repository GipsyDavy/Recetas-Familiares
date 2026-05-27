CREATE TABLE family_notes (
    id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    recipe_id CHAR(36) NULL,
    title VARCHAR(180) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_family_notes_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_family_notes_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);

CREATE INDEX ix_family_notes_family_active ON family_notes (family_id, deleted, pinned, updated_at);
CREATE INDEX ix_family_notes_recipe ON family_notes (recipe_id);
CREATE INDEX ix_family_notes_sync ON family_notes (updated_at, sync_version, deleted);
