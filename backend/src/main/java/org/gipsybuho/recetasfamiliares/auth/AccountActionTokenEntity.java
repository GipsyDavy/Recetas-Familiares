package org.gipsybuho.recetasfamiliares.auth;

import java.time.Instant;
import java.util.UUID;

import org.gipsybuho.recetasfamiliares.users.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_action_tokens")
public class AccountActionTokenEntity {

    @Id
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "varchar(36)")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 40)
    private AccountActionTokenType type;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected AccountActionTokenEntity() {
    }

    public AccountActionTokenEntity(
            UserEntity user,
            AccountActionTokenType type,
            String tokenHash,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.type = type;
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

    public boolean isActive(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now) && !user.isDeleted();
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
