package org.gipsybuho.recetasfamiliares.recipes;

import java.math.BigDecimal;
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

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredientEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, columnDefinition = "varchar(36)")
    private RecipeEntity recipe;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(length = 40)
    private String unit;

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

    protected RecipeIngredientEntity() {
    }

    public RecipeIngredientEntity(
            RecipeEntity recipe,
            int position,
            String name,
            BigDecimal quantity,
            String unit,
            String note
    ) {
        this.recipe = recipe;
        this.position = position;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.note = note;
    }

    public RecipeIngredientEntity(
            String id,
            RecipeEntity recipe,
            int position,
            String name,
            BigDecimal quantity,
            String unit,
            String note
    ) {
        this(recipe, position, name, quantity, unit, note);
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

    public String getName() {
        return name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
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

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            deletedAt = Instant.now();
            touch();
        }
    }

    public void applySync(int position, String name, BigDecimal quantity, String unit, String note, boolean deleted) {
        this.position = position;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.note = note;
        this.deleted = deleted;
        this.deletedAt = deleted ? Instant.now() : null;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
        syncVersion++;
    }
}
