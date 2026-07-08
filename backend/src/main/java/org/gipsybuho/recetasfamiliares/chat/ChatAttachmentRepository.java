package org.gipsybuho.recetasfamiliares.chat;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachmentEntity, String> {

    @Query("""
            SELECT a.message.family.id
            FROM ChatAttachmentEntity a
            WHERE a.deleted = false
              AND (a.storagePath = :storagePath OR a.thumbnailStoragePath = :storagePath)
            """)
    List<String> findOwningFamilyIdsByStoragePath(@Param("storagePath") String storagePath);
}
