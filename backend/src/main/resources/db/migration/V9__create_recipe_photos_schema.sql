CREATE TABLE recipe_photos (
    id CHAR(36) NOT NULL,
    recipe_id CHAR(36) NOT NULL,
    position INT NOT NULL,
    url VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(1000) NULL,
    caption VARCHAR(255) NULL,
    content_type VARCHAR(80) NULL,
    size_bytes BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recipe_photos_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
);

CREATE INDEX ix_recipe_photos_recipe_active ON recipe_photos (recipe_id, deleted, position);
CREATE INDEX ix_recipe_photos_sync ON recipe_photos (updated_at, sync_version, deleted);
