package org.gipsybuho.recetasfamiliares.ratings;

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
import org.gipsybuho.recetasfamiliares.users.UserEntity;

@Entity
@Table(name = "recipe_ratings")
public class RecipeRatingEntity {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, columnDefinition = "CHAR(36)")
    private RecipeEntity recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UserEntity user;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private int stars;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(nullable = false)
    private boolean deleted;

    protected RecipeRatingEntity() {}

    public RecipeRatingEntity(RecipeEntity recipe, FamilyEntity family, UserEntity user,
                               int stars, String comment) {
        this.id = UUID.randomUUID().toString();
        this.recipe = recipe;
        this.family = family;
        this.user = user;
        this.stars = stars;
        this.comment = comment;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.syncVersion = 1L;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        this.syncVersion++;
    }

    public void update(int stars, String comment) {
        this.stars = stars;
        this.comment = comment;
    }

    public void softDelete() { this.deleted = true; }

    public String getId()              { return id; }
    public String getRecipeId()        { return recipe.getId(); }
    public String getFamilyId()        { return family.getId(); }
    public String getUserId()          { return user.getId(); }
    public String getUserDisplayName() { return user.getDisplayName(); }
    public int getStars()              { return stars; }
    public String getComment()         { return comment; }
    public Instant getCreatedAt()      { return createdAt; }
    public Instant getUpdatedAt()      { return updatedAt; }
    public long getSyncVersion()       { return syncVersion; }
    public boolean isDeleted()         { return deleted; }
}
