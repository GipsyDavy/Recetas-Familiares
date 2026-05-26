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
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
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
        currentToken.revoke(Instant.now(), replacement.entity().getId());
        refreshTokenRepository.save(currentToken);
        return replacement;
    }

    void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.revoke(Instant.now(), null);
                    refreshTokenRepository.save(token);
                });
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
