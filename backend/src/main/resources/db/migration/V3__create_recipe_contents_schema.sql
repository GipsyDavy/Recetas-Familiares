CREATE TABLE recipe_ingredients (
    id VARCHAR(36) NOT NULL,
    recipe_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    quantity DECIMAL(10,3) NULL,
    unit VARCHAR(40) NULL,
    note VARCHAR(255) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);

CREATE INDEX ix_recipe_ingredients_recipe_active ON recipe_ingredients (recipe_id, deleted, position);
CREATE INDEX ix_recipe_ingredients_sync ON recipe_ingredients (updated_at, sync_version, deleted);

CREATE TABLE recipe_steps (
    id VARCHAR(36) NOT NULL,
    recipe_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    instruction VARCHAR(2000) NOT NULL,
    timer_minutes INT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);

CREATE INDEX ix_recipe_steps_recipe_active ON recipe_steps (recipe_id, deleted, position);
CREATE INDEX ix_recipe_steps_sync ON recipe_steps (updated_at, sync_version, deleted);
