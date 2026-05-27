package org.gipsybuho.recetasfamiliares.menus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;

@Entity
@Table(name = "menu_items")
public class MenuItemEntity {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", columnDefinition = "CHAR(36)")
    private RecipeEntity recipe;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 40)
    private MenuMealType mealType;

    @Column(length = 255)
    private String note;

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

    protected MenuItemEntity() {
    }

    public MenuItemEntity(FamilyEntity family, RecipeEntity recipe, LocalDate plannedDate, MenuMealType mealType, String note) {
        this.family = family;
        this.recipe = recipe;
        this.plannedDate = plannedDate;
        this.mealType = mealType;
        this.note = note;
    }

    public MenuItemEntity(String id, FamilyEntity family, RecipeEntity recipe, LocalDate plannedDate, MenuMealType mealType, String note) {
        this(family, recipe, plannedDate, mealType, note);
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

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public MenuMealType getMealType() {
        return mealType;
    }

    public String getNote() {
        return note;
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

    public void update(RecipeEntity recipe, LocalDate plannedDate, MenuMealType mealType, String note) {
        this.recipe = recipe;
        this.plannedDate = plannedDate;
        this.mealType = mealType;
        this.note = note;
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
