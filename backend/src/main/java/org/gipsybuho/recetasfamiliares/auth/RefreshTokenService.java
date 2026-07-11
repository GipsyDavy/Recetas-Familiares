package org.gipsybuho.recetasfamiliares.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshTokenTtl;
    private final Duration revokedTokenRetention;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays,
            @Value("${app.security.jwt.revoked-refresh-token-retention-days:7}") long revokedTokenRetentionDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.revokedTokenRetention = Duration.ofDays(revokedTokenRetentionDays);
    }

    IssuedRefreshToken issue(UserEntity user) {
        String rawToken = generateRawToken();
        RefreshTokenEntity entity = new RefreshTokenEntity(user, hash(rawToken), Instant.now().plus(refreshTokenTtl));
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawToken, entity);
    }

    RefreshTokenEntity requireActive(String rawToken) {
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!entity.isActive(Instant.now()) || entity.getUser().isDeleted()) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return entity;
    }

    IssuedRefreshToken rotate(RefreshTokenEntity currentToken) {
        IssuedRefreshToken replacement = issue(currentToken.getUser());
        // Revocacion condicional atomica: si otro refresh concurrente ya roto
        // este token, el UPDATE no afecta filas y el reemplazo recien emitido
        // se descarta sin haberse entregado nunca al cliente.
        int revoked = refreshTokenRepository.revokeIfActive(
                currentToken.getId(), Instant.now(), replacement.entity().getId());
        if (revoked == 0) {
            refreshTokenRepository.delete(replacement.entity());
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return replacement;
    }

    void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.revoke(Instant.now(), null);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(String userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
    }

    @Scheduled(cron = "${app.security.jwt.refresh-token-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredAndOldRevokedTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpiredOrRevokedBefore(now, now.minus(revokedTokenRetention));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    record IssuedRefreshToken(String rawToken, RefreshTokenEntity entity) {
    }
}
