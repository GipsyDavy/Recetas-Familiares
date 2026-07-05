ALTER TABLE recipe_photos
    ADD COLUMN storage_path VARCHAR(255) NULL;

CREATE INDEX ix_recipe_photos_storage_path ON recipe_photos (storage_path);
