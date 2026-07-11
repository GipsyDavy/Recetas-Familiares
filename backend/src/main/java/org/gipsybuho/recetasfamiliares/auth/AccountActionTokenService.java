package org.gipsybuho.recetasfamiliares.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountActionTokenService {

    private final AccountActionTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountActionTokenService(AccountActionTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public IssuedAccountToken issue(UserEntity user, AccountActionTokenType type, Duration ttl) {
        Instant now = Instant.now();
        tokenRepository.consumeOutstandingForUserAndType(user.getId(), type, now);

        String rawToken = generateRawToken();
        AccountActionTokenEntity entity = new AccountActionTokenEntity(
                user,
                type,
                hash(rawToken),
                now.plus(ttl)
        );
        tokenRepository.save(entity);
        return new IssuedAccountToken(rawToken);
    }

    @Transactional
    public UserEntity consume(String rawToken, AccountActionTokenType type) {
        AccountActionTokenEntity token = tokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));
        Instant now = Instant.now();
        if (!token.isActive(now)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        token.consume(now);
        tokenRepository.save(token);
        return token.getUser();
    }

    @Scheduled(cron = "${app.account.token-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredAndConsumedTokens() {
        Instant now = Instant.now();
        tokenRepository.deleteExpiredOrConsumedBefore(now, now.minus(Duration.ofDays(7)));
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

    public record IssuedAccountToken(String rawToken) {
    }
}
