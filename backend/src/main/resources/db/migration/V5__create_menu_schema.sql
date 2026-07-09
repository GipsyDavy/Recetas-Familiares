CREATE TABLE menu_items (
    id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    recipe_id CHAR(36) NULL,
    planned_date DATE NOT NULL,
    meal_type VARCHAR(40) NOT NULL,
    note VARCHAR(255) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_menu_items_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT fk_menu_items_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);

CREATE INDEX ix_menu_items_family_week ON menu_items (family_id, planned_date, deleted);
CREATE INDEX ix_menu_items_recipe ON menu_items (recipe_id);
CREATE INDEX ix_menu_items_sync ON menu_items (updated_at, sync_version, deleted);
