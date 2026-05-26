package org.gipsybuho.recetasfamiliares.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.security.jwt.secret}") String jwtSecret,
            @Value("${app.security.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes
    ) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public IssuedAccessToken issue(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        String token = Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("displayName", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new IssuedAccessToken(token, accessTokenTtl.toSeconds());
    }

    public String validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidJwtException("Invalid access token", exception);
        }
    }

    public record IssuedAccessToken(String token, long expiresInSeconds) {
    }
}
