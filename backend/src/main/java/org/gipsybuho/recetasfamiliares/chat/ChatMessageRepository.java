package org.gipsybuho.recetasfamiliares.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    Optional<ChatMessageEntity> findByIdAndFamily_Id(String id, String familyId);

    Optional<ChatMessageEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    /**
     * Historial descendente (mas reciente primero) filtrado por la marca de
     * limpieza del usuario.
     */
    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.family.id = :familyId
              AND m.createdAt > :clearedBefore
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<ChatMessageEntity> findHistory(
            @Param("familyId") String familyId,
            @Param("clearedBefore") Instant clearedBefore,
            Pageable pageable
    );

    /**
     * Pagina anterior al cursor usando {@code (createdAt, id)} como orden estable.
     */
    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.family.id = :familyId
              AND m.createdAt > :clearedBefore
              AND (
                    m.createdAt < :beforeCreatedAt
                    OR (m.createdAt = :beforeCreatedAt AND m.id < :beforeId)
              )
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<ChatMessageEntity> findHistoryBefore(
            @Param("familyId") String familyId,
            @Param("clearedBefore") Instant clearedBefore,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeId") String beforeId,
            Pageable pageable
    );

    /**
     * Exportacion: todos los mensajes visibles para el usuario en orden ascendente.
     */
    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.family.id = :familyId
              AND m.createdAt > :clearedBefore
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<ChatMessageEntity> findVisibleForExport(
            @Param("familyId") String familyId,
            @Param("clearedBefore") Instant clearedBefore
    );

    @Query("SELECT MAX(m.createdAt) FROM ChatMessageEntity m WHERE m.family.id = :familyId AND m.deleted = false")
    Instant findLatestCreatedAt(@Param("familyId") String familyId);
}
