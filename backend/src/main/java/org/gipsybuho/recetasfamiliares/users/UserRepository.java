package org.gipsybuho.recetasfamiliares.users;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<UserEntity> findByIdAndDeletedFalse(String id);

    @Query("""
            SELECT u.id
            FROM UserEntity u
            WHERE u.deleted = false
              AND (u.avatarUrl = :absoluteUrl OR u.avatarUrl = :relativePath)
            """)
    List<String> findIdsByAvatarLocalUrl(
            @Param("absoluteUrl") String absoluteUrl,
            @Param("relativePath") String relativePath
    );
}
