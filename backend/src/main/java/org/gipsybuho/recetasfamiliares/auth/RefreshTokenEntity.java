package org.gipsybuho.recetasfamiliares.auth;

import java.time.Instant;
import java.util.UUID;

import org.gipsybuho.recetasfamiliares.users.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id", length = 36, columnDefinition = "varchar(36)")
    private String replacedByTokenId;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(UserEntity user, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    public UserEntity getUser() {
        return user;
    }

    public String getId() {
        return id;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, String replacementTokenId) {
        revokedAt = now;
        replacedByTokenId = replacementTokenId;
    }
}
