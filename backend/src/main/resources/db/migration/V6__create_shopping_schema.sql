CREATE TABLE shopping_lists (
    id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    name VARCHAR(180) NOT NULL,
    planned_from DATE NULL,
    planned_to DATE NULL,
    note VARCHAR(255) NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shopping_lists_family FOREIGN KEY (family_id) REFERENCES families (id)
);

CREATE TABLE shopping_list_items (
    id VARCHAR(36) NOT NULL,
    shopping_list_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    quantity DECIMAL(10,3) NULL,
    unit VARCHAR(40) NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(255) NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shopping_list_items_list FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists (id)
);

CREATE INDEX ix_shopping_lists_family_active ON shopping_lists (family_id, deleted, updated_at);
CREATE INDEX ix_shopping_lists_sync ON shopping_lists (updated_at, sync_version, deleted);
CREATE INDEX ix_shopping_list_items_list_active ON shopping_list_items (shopping_list_id, deleted, position);
CREATE INDEX ix_shopping_list_items_sync ON shopping_list_items (updated_at, sync_version, deleted);
