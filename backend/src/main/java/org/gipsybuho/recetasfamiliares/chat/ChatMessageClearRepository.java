package org.gipsybuho.recetasfamiliares.chat;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageClearRepository extends JpaRepository<ChatMessageClearEntity, String> {

    Optional<ChatMessageClearEntity> findByFamily_IdAndUser_Id(String familyId, String userId);
}
