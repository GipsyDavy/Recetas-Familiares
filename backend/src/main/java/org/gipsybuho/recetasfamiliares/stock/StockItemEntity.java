package org.gipsybuho.recetasfamiliares.stock;

import java.math.BigDecimal;
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
@Table(name = "stock_items")
public class StockItemEntity {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private FamilyEntity family;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(length = 40)
    private String unit;

    @Column(name = "low_stock_threshold", precision = 10, scale = 3)
    private BigDecimal lowStockThreshold;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

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

    protected StockItemEntity() {
    }

    public StockItemEntity(
            FamilyEntity family,
            String name,
            BigDecimal quantity,
            String unit,
            BigDecimal lowStockThreshold,
            LocalDate expiresAt,
            String note
    ) {
        this.family = family;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.lowStockThreshold = lowStockThreshold;
        this.expiresAt = expiresAt;
        this.note = note;
    }

    public StockItemEntity(
            String id,
            FamilyEntity family,
            String name,
            BigDecimal quantity,
            String unit,
            BigDecimal lowStockThreshold,
            LocalDate expiresAt,
            String note
    ) {
        this(family, name, quantity, unit, lowStockThreshold, expiresAt, note);
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getLowStockThreshold() {
        return lowStockThreshold;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
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

    public void update(
            String name,
            BigDecimal quantity,
            String unit,
            BigDecimal lowStockThreshold,
            LocalDate expiresAt,
            String note
    ) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.lowStockThreshold = lowStockThreshold;
        this.expiresAt = expiresAt;
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
