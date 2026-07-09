package org.gipsybuho.recetasfamiliares.notes;

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

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;

@Entity
@Table(name = "family_notes")
public class FamilyNoteEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "varchar(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", columnDefinition = "varchar(36)")
    private RecipeEntity recipe;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false)
    private boolean pinned;

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

    protected FamilyNoteEntity() {
    }

    public FamilyNoteEntity(FamilyEntity family, RecipeEntity recipe, String title, String body, boolean pinned) {
        this.family = family;
        this.recipe = recipe;
        this.title = title;
        this.body = body;
        this.pinned = pinned;
    }

    public FamilyNoteEntity(String id, FamilyEntity family, RecipeEntity recipe, String title, String body, boolean pinned) {
        this(family, recipe, title, body, pinned);
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

    public String getFamilyId() {
        return family.getId();
    }

    public String getRecipeId() {
        return recipe == null ? null : recipe.getId();
    }

    public String getRecipeTitle() {
        return recipe == null ? null : recipe.getTitle();
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isPinned() {
        return pinned;
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

    public void update(RecipeEntity recipe, String title, String body, boolean pinned) {
        this.recipe = recipe;
        this.title = title;
        this.body = body;
        this.pinned = pinned;
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
