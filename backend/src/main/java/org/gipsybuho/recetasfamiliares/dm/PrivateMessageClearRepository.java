package org.gipsybuho.recetasfamiliares.dm;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateMessageClearRepository extends JpaRepository<PrivateMessageClearEntity, String> {

    Optional<PrivateMessageClearEntity> findByConversation_IdAndUser_Id(String conversationId, String userId);
}
