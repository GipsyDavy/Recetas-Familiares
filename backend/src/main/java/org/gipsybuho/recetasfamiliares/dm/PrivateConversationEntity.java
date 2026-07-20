package org.gipsybuho.recetasfamiliares.dm;

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
import org.gipsybuho.recetasfamiliares.users.UserEntity;

/**
 * Conversacion privada 1:1 entre dos miembros de una misma familia. El par de
 * usuarios va normalizado (userA.id < userB.id lexicograficamente) para que
 * exista como maximo una conversacion por par y familia, sin importar quien
 * la inicio.
 */
@Entity
@Table(name = "private_conversations")
public class PrivateConversationEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "varchar(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity userB;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(nullable = false)
    private boolean deleted;

    protected PrivateConversationEntity() {
    }

    public PrivateConversationEntity(FamilyEntity family, UserEntity userA, UserEntity userB) {
        this.family = family;
        this.userA = userA;
        this.userB = userB;
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

    public String getUserAId() {
        return userA.getId();
    }

    public String getUserBId() {
        return userB.getId();
    }

    /** true si userId es uno de los dos participantes de esta conversacion. */
    public boolean hasParticipant(String userId) {
        return getUserAId().equals(userId) || getUserBId().equals(userId);
    }

    /** El otro participante distinto de userId. Llamar solo si hasParticipant(userId) es true. */
    public String otherParticipant(String userId) {
        return getUserAId().equals(userId) ? getUserBId() : getUserAId();
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
}
