package org.gipsybuho.recetasfamiliares.auth;

import java.util.Optional;
import java.time.Instant;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            delete from RefreshTokenEntity token
            where token.expiresAt < :now
               or token.revokedAt < :revokedBefore
            """)
    int deleteExpiredOrRevokedBefore(
            @Param("now") Instant now,
            @Param("revokedBefore") Instant revokedBefore
    );

    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int revokeAllActiveByUserId(@Param("userId") String userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshTokenEntity token
            set token.revokedAt = :now, token.replacedByTokenId = :replacementId
            where token.id = :id and token.revokedAt is null
            """)
    int revokeIfActive(
            @Param("id") String id,
            @Param("now") Instant now,
            @Param("replacementId") String replacementId
    );
}
