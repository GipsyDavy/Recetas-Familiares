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

@Entity
@Table(name = "chat_attachments")
public class ChatAttachmentEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, columnDefinition = "varchar(36)")
    private ChatMessageEntity message;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "thumbnail_storage_path", length = 512)
    private String thumbnailStoragePath;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private Integer width;

    private Integer height;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ChatAttachmentEntity() {
    }

    public ChatAttachmentEntity(
            ChatMessageEntity message,
            String url,
            String thumbnailUrl,
            String storagePath,
            String thumbnailStoragePath,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {
        this.message = message;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.storagePath = storagePath;
        this.thumbnailStoragePath = thumbnailStoragePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
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

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getThumbnailStoragePath() {
        return thumbnailStoragePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            deletedAt = Instant.now();
            updatedAt = deletedAt;
        }
    }
}
