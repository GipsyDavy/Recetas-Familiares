CREATE TABLE recipe_ratings (
    id CHAR(36) NOT NULL,
    recipe_id CHAR(36) NOT NULL,
    family_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    stars TINYINT NOT NULL,
    comment VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    sync_version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_recipe_ratings_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT fk_recipe_ratings_family FOREIGN KEY (family_id) REFERENCES families (id),
    CONSTRAINT chk_recipe_ratings_stars CHECK (stars BETWEEN 1 AND 5)
);

CREATE INDEX ix_recipe_ratings_recipe ON recipe_ratings (recipe_id, deleted);
CREATE INDEX ix_recipe_ratings_user ON recipe_ratings (user_id, recipe_id, deleted);
CREATE INDEX ix_recipe_ratings_sync ON recipe_ratings (updated_at, sync_version, deleted);
