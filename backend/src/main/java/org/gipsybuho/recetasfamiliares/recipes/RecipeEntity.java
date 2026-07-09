package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;
import java.util.UUID;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipes")
public class RecipeEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "varchar(36)")
    private FamilyEntity family;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 1000)
    private String description;

    private Integer servings;

    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    @Column(name = "cook_minutes")
    private Integer cookMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private RecipeDifficulty difficulty;

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

    protected RecipeEntity() {
    }

    public RecipeEntity(
            FamilyEntity family,
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty
    ) {
        this.family = family;
        this.title = title;
        this.description = description;
        this.servings = servings;
        this.prepMinutes = prepMinutes;
        this.cookMinutes = cookMinutes;
        this.difficulty = difficulty;
    }

    public RecipeEntity(
            String id,
            FamilyEntity family,
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty
    ) {
        this(family, title, description, servings, prepMinutes, cookMinutes, difficulty);
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getServings() {
        return servings;
    }

    public Integer getPrepMinutes() {
        return prepMinutes;
    }

    public Integer getCookMinutes() {
        return cookMinutes;
    }

    public RecipeDifficulty getDifficulty() {
        return difficulty;
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
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty
    ) {
        this.title = title;
        this.description = description;
        this.servings = servings;
        this.prepMinutes = prepMinutes;
        this.cookMinutes = cookMinutes;
        this.difficulty = difficulty;
        touch();
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            deletedAt = Instant.now();
            touch();
        }
    }

    public void markContentChanged() {
        touch();
    }

    public void applySync(
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            RecipeDifficulty difficulty,
            boolean deleted
    ) {
        this.title = title;
        this.description = description;
        this.servings = servings;
        this.prepMinutes = prepMinutes;
        this.cookMinutes = cookMinutes;
        this.difficulty = difficulty;
        this.deleted = deleted;
        this.deletedAt = deleted ? Instant.now() : null;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
        syncVersion++;
    }
}
