package org.gipsybuho.recetasfamiliares.photos;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;

@Entity
@Table(name = "recipe_photos")
public class RecipePhotoEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, columnDefinition = "varchar(36)")
    private RecipeEntity recipe;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(length = 255)
    private String caption;

    @Column(name = "content_type", length = 80)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RecipePhotoEntity() {
    }

    public RecipePhotoEntity(
            RecipeEntity recipe,
            int position,
            String url,
            String thumbnailUrl,
            String caption,
            String contentType,
            Long sizeBytes
    ) {
        this(recipe, position, url, thumbnailUrl, caption, contentType, sizeBytes, null);
    }

    public RecipePhotoEntity(
            RecipeEntity recipe,
            int position,
            String url,
            String thumbnailUrl,
            String caption,
            String contentType,
            Long sizeBytes,
            String storagePath
    ) {
        this.recipe = recipe;
        this.position = position;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.caption = caption;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
    }

    public RecipePhotoEntity(
            String id,
            RecipeEntity recipe,
            int position,
            String url,
            String thumbnailUrl,
            String caption,
            String contentType,
            Long sizeBytes
    ) {
        this(recipe, position, url, thumbnailUrl, caption, contentType, sizeBytes);
        this.id = id;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getRecipeId() {
        return recipe.getId();
    }

    public int getPosition() {
        return position;
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getCaption() {
        return caption;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getSyncVersion() {
        return syncVersion;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void update(
            int position,
            String url,
            String thumbnailUrl,
            String caption,
            String contentType,
            Long sizeBytes
    ) {
        boolean urlChanged = this.url == null || !this.url.equals(url);
        this.position = position;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.caption = caption;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        if (urlChanged) {
            this.storagePath = null;
        }
        this.deleted = false;
        this.deletedAt = null;
        touch();
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            deletedAt = Instant.now();
            touch();
        }
    }

    private void touch() {
        updatedAt = Instant.now();
        syncVersion++;
    }
}
