package org.gipsybuho.recetasfamiliares.recipes;

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
@Table(name = "recipe_steps")
public class RecipeStepEntity {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, columnDefinition = "CHAR(36)")
    private RecipeEntity recipe;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 2000)
    private String instruction;

    @Column(name = "timer_minutes")
    private Integer timerMinutes;

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

    protected RecipeStepEntity() {
    }

    public RecipeStepEntity(RecipeEntity recipe, int position, String instruction, Integer timerMinutes) {
        this.recipe = recipe;
        this.position = position;
        this.instruction = instruction;
        this.timerMinutes = timerMinutes;
    }

    public RecipeStepEntity(
            String id,
            RecipeEntity recipe,
            int position,
            String instruction,
            Integer timerMinutes
    ) {
        this(recipe, position, instruction, timerMinutes);
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

    public String getInstruction() {
        return instruction;
    }

    public Integer getTimerMinutes() {
        return timerMinutes;
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

    public void applySync(int position, String instruction, Integer timerMinutes, boolean deleted) {
        this.position = position;
        this.instruction = instruction;
        this.timerMinutes = timerMinutes;
        this.deleted = deleted;
        this.deletedAt = deleted ? Instant.now() : null;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
        syncVersion++;
    }
}
