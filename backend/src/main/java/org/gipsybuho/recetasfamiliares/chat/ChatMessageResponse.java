package org.gipsybuho.recetasfamiliares.chat;

import java.time.Instant;
import java.util.List;

/**
 * DTO de mensaje de chat. {@code body} es null cuando el mensaje esta borrado.
 * Entidad sincronizable estandar: id, createdAt, updatedAt, syncVersion, deleted.
 */
public record ChatMessageResponse(
        String id,
        String familyId,
        String authorUserId,
        String authorDisplayName,
        String body,
        List<ChatAttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
    static ChatMessageResponse from(ChatMessageEntity message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getFamilyId(),
                message.getAuthorUserId(),
                message.getAuthorDisplayName(),
                message.getBody(),
                message.getAttachments().stream().map(ChatAttachmentResponse::from).toList(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getSyncVersion(),
                message.isDeleted()
        );
    }
}
