CREATE TABLE favorite_recipes (
    id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    recipe_id VARCHAR(36) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_favorite_recipes_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_favorite_recipes_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT uq_favorite_recipes_family_recipe UNIQUE (family_id, recipe_id)
);

CREATE INDEX ix_favorite_recipes_family_active ON favorite_recipes (family_id, deleted, updated_at);
CREATE INDEX ix_favorite_recipes_recipe ON favorite_recipes (recipe_id);
CREATE INDEX ix_favorite_recipes_sync ON favorite_recipes (updated_at, sync_version, deleted);
