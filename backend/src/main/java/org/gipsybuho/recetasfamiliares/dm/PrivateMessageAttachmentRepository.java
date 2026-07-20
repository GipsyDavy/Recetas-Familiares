package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateMessageAttachmentRepository extends JpaRepository<PrivateMessageAttachmentEntity, String> {

    @Query("""
            SELECT a.message.conversation.id
            FROM PrivateMessageAttachmentEntity a
            WHERE a.deleted = false
              AND (a.storagePath = :storagePath OR a.thumbnailStoragePath = :storagePath)
            """)
    List<String> findOwningConversationIdsByStoragePath(@Param("storagePath") String storagePath);
}
