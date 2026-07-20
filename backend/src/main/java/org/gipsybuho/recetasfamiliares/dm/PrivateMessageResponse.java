package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.List;

/**
 * DTO de mensaje de chat privado. {@code body} es null cuando el mensaje esta
 * borrado. Entidad sincronizable estandar: id, createdAt, updatedAt,
 * syncVersion, deleted.
 */
public record PrivateMessageResponse(
        String id,
        String conversationId,
        String authorUserId,
        String authorDisplayName,
        String body,
        List<PrivateMessageAttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
    static PrivateMessageResponse from(PrivateMessageEntity message) {
        return new PrivateMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getAuthorUserId(),
                message.getAuthorDisplayName(),
                message.getBody(),
                message.getAttachments().stream().map(PrivateMessageAttachmentResponse::from).toList(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getSyncVersion(),
                message.isDeleted()
        );
    }
}
