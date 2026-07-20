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

import org.gipsybuho.recetasfamiliares.users.UserEntity;

/**
 * Marca de limpieza del chat privado por usuario, igual que
 * ChatMessageClearEntity pero por conversacion en vez de por familia.
 */
@Entity
@Table(name = "private_message_clears")
public class PrivateMessageClearEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, columnDefinition = "varchar(36)")
    private PrivateConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity user;

    @Column(name = "cleared_before", nullable = false)
    private Instant clearedBefore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrivateMessageClearEntity() {
    }

    public PrivateMessageClearEntity(PrivateConversationEntity conversation, UserEntity user, Instant clearedBefore) {
        this.conversation = conversation;
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
