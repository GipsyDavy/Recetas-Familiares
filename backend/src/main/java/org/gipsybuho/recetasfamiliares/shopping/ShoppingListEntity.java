package org.gipsybuho.recetasfamiliares.shopping;

import java.time.Instant;
import java.time.LocalDate;
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

@Entity
@Table(name = "shopping_lists")
public class ShoppingListEntity {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private FamilyEntity family;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "planned_from")
    private LocalDate plannedFrom;

    @Column(name = "planned_to")
    private LocalDate plannedTo;

    @Column(length = 255)
    private String note;

    @Column(nullable = false)
    private boolean completed;

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

    protected ShoppingListEntity() {
    }

    public ShoppingListEntity(FamilyEntity family, String name, LocalDate plannedFrom, LocalDate plannedTo, String note, boolean completed) {
        this.family = family;
        this.name = name;
        this.plannedFrom = plannedFrom;
        this.plannedTo = plannedTo;
        this.note = note;
        this.completed = completed;
    }

    public ShoppingListEntity(String id, FamilyEntity family, String name, LocalDate plannedFrom, LocalDate plannedTo, String note, boolean completed) {
        this(family, name, plannedFrom, plannedTo, note, completed);
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

    public String getName() {
        return name;
    }

    public LocalDate getPlannedFrom() {
        return plannedFrom;
    }

    public LocalDate getPlannedTo() {
        return plannedTo;
    }

    public String getNote() {
        return note;
    }

    public boolean isCompleted() {
        return completed;
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

    public void update(String name, LocalDate plannedFrom, LocalDate plannedTo, String note, boolean completed) {
        this.name = name;
        this.plannedFrom = plannedFrom;
        this.plannedTo = plannedTo;
        this.note = note;
        this.completed = completed;
        this.deleted = false;
        this.deletedAt = null;
        touch();
    }

    public void markItemsChanged() {
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
