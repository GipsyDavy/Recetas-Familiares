package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessageEntity, String> {

    Optional<PrivateMessageEntity> findByIdAndConversation_Id(String id, String conversationId);

    Optional<PrivateMessageEntity> findByIdAndConversation_IdAndDeletedFalse(String id, String conversationId);

    Optional<PrivateMessageEntity> findFirstByConversation_IdOrderByCreatedAtDescIdDesc(String conversationId);

    @Query("""
            SELECT m FROM PrivateMessageEntity m
            WHERE m.conversation.id = :conversationId
              AND m.createdAt > :clearedBefore
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<PrivateMessageEntity> findHistory(
            @Param("conversationId") String conversationId,
            @Param("clearedBefore") Instant clearedBefore,
            Pageable pageable
    );

    @Query("""
            SELECT m FROM PrivateMessageEntity m
            WHERE m.conversation.id = :conversationId
              AND m.createdAt > :clearedBefore
              AND (
                    m.createdAt < :beforeCreatedAt
                    OR (m.createdAt = :beforeCreatedAt AND m.id < :beforeId)
              )
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<PrivateMessageEntity> findHistoryBefore(
            @Param("conversationId") String conversationId,
            @Param("clearedBefore") Instant clearedBefore,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeId") String beforeId,
            Pageable pageable
    );

    @Query("""
            SELECT m FROM PrivateMessageEntity m
            WHERE m.conversation.id = :conversationId
              AND m.createdAt > :clearedBefore
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<PrivateMessageEntity> findVisibleForExport(
            @Param("conversationId") String conversationId,
            @Param("clearedBefore") Instant clearedBefore
    );
}
