ALTER TABLE recipes
    ADD COLUMN created_by_user_id VARCHAR(36) NULL;

ALTER TABLE recipes
    ADD CONSTRAINT fk_recipes_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id);

CREATE INDEX ix_recipes_created_by_user ON recipes (created_by_user_id);
