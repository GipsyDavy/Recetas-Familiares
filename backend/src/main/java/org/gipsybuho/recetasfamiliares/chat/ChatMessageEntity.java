package org.gipsybuho.recetasfamiliares.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.users.UserEntity;

/**
 * Mensaje de texto del chat familiar (fase 1). Modulo independiente de las notas:
 * un solo hilo por familia, ownership por familia, historial paginado por cursor.
 * Los emojis Unicode viajan de forma nativa dentro de {@code body}.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false, columnDefinition = "varchar(36)")
    private FamilyEntity family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity author;

    @Column(length = 2000)
    private String body;

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

    // BatchSize evita N+1 al mapear historial/export: los adjuntos de varios
    // mensajes se cargan en lotes en vez de una consulta por mensaje. Compatible
    // con la paginacion por cursor del historial (a diferencia de un fetch join).
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 50)
    private List<ChatAttachmentEntity> attachments = new ArrayList<>();

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(String id, FamilyEntity family, UserEntity author, String body) {
        this.id = id;
        this.family = family;
        this.author = author;
        this.body = body;
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

    public String getAuthorUserId() {
        return author.getId();
    }

    public String getAuthorDisplayName() {
        return author.getDisplayName();
    }

    public String getBody() {
        return deleted ? null : body;
    }

    public List<ChatAttachmentEntity> getAttachments() {
        if (deleted) {
            return List.of();
        }
        return attachments.stream()
                .filter(attachment -> !attachment.isDeleted())
                .toList();
    }

    public void addAttachment(ChatAttachmentEntity attachment) {
        attachments.add(attachment);
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

    public void editBody(String body) {
        if (!deleted) {
            this.body = body;
            updatedAt = Instant.now();
            syncVersion++;
        }
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            body = null;
            attachments.forEach(ChatAttachmentEntity::softDelete);
            deletedAt = Instant.now();
            updatedAt = Instant.now();
            syncVersion++;
        }
    }
}
