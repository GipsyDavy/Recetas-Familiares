package org.gipsybuho.recetasfamiliares.chat;

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
 * Marca de limpieza del chat por usuario. Cada miembro puede ocultar su propia
 * vista del historial; los mensajes con {@code createdAt <= clearedBefore} dejan
 * de mostrarse solo para ese usuario. No borra mensajes compartidos.
 */
@Entity
@Table(name = "chat_message_clears")
public class ChatMessageClearEntity {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UserEntity user;

    @Column(name = "cleared_before", nullable = false)
    private Instant clearedBefore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatMessageClearEntity() {
    }

    public ChatMessageClearEntity(FamilyEntity family, UserEntity user, Instant clearedBefore) {
        this.family = family;
        this.user = user;
        this.clearedBefore = clearedBefore;
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

    public Instant getClearedBefore() {
        return clearedBefore;
    }

    public void setClearedBefore(Instant clearedBefore) {
        this.clearedBefore = clearedBefore;
    }
}
