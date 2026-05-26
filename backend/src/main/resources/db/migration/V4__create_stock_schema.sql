CREATE TABLE stock_items (
    id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    name VARCHAR(180) NOT NULL,
    quantity DECIMAL(10,3) NULL,
    unit VARCHAR(40) NULL,
    low_stock_threshold DECIMAL(10,3) NULL,
    expires_at DATE NULL,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_items_family FOREIGN KEY (family_id) REFERENCES families (id)
);

CREATE INDEX ix_stock_items_family_active ON stock_items (family_id, deleted, updated_at);
CREATE INDEX ix_stock_items_sync ON stock_items (updated_at, sync_version, deleted);
