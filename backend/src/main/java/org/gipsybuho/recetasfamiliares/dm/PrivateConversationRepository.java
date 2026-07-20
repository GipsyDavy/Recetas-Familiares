package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateConversationRepository extends JpaRepository<PrivateConversationEntity, String> {

    Optional<PrivateConversationEntity> findByFamily_IdAndUserA_IdAndUserB_Id(
            String familyId, String userAId, String userBId);

    Optional<PrivateConversationEntity> findByIdAndDeletedFalse(String id);

    @Query("""
            SELECT c FROM PrivateConversationEntity c
            WHERE c.family.id = :familyId
              AND (c.userA.id = :userId OR c.userB.id = :userId)
              AND c.deleted = false
            """)
    List<PrivateConversationEntity> findAllForParticipant(
            @Param("familyId") String familyId, @Param("userId") String userId);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM PrivateConversationEntity c
            WHERE c.id = :conversationId
              AND (c.userA.id = :userId OR c.userB.id = :userId)
            """)
    boolean existsByIdAndParticipant(@Param("conversationId") String conversationId, @Param("userId") String userId);
}
