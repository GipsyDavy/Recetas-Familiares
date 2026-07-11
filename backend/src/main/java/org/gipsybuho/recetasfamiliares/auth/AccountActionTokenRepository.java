package org.gipsybuho.recetasfamiliares.auth;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionTokenEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountActionTokenEntity> findByTokenHashAndType(String tokenHash, AccountActionTokenType type);

    @Modifying
    @Query("""
            update AccountActionTokenEntity token
            set token.consumedAt = :now
            where token.user.id = :userId
              and token.type = :type
              and token.consumedAt is null
            """)
    int consumeOutstandingForUserAndType(
            @Param("userId") String userId,
            @Param("type") AccountActionTokenType type,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            delete from AccountActionTokenEntity token
            where token.expiresAt < :now
               or token.consumedAt < :consumedBefore
            """)
    int deleteExpiredOrConsumedBefore(
            @Param("now") Instant now,
            @Param("consumedBefore") Instant consumedBefore
    );
}
