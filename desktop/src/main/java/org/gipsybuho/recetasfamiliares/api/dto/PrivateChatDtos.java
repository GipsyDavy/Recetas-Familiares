package org.gipsybuho.recetasfamiliares.api.dto;

import java.util.List;

/**
 * DTOs del chat privado 1:1. Reflejan el contrato REST/WS del backend bajo
 * {@code /api/v1/families/{familyId}/conversations} (paquete backend {@code dm}).
 * Las marcas de tiempo se modelan como String ISO-8601 (UTC), igual que ChatDtos.
 */
public final class PrivateChatDtos {

    private PrivateChatDtos() {}

    public record PrivateAttachment(
            String id,
            String url,
            String thumbnailUrl,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {
    }

    public record PrivateMessage(
            String id,
            String conversationId,
            String authorUserId,
            String authorDisplayName,
            String body,
            List<PrivateAttachment> attachments,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {
        public List<PrivateAttachment> attachmentsOrEmpty() {
            return attachments != null ? attachments : List.of();
        }

        /** Descarta frames WS incompletos o mal formados antes de mostrarlos. */
        public boolean isUsable() {
            return notBlank(id)
                    && notBlank(conversationId)
                    && notBlank(authorUserId)
                    && notBlank(authorDisplayName)
                    && notBlank(createdAt);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** Fila de la bandeja: la conversacion vista desde el usuario que la solicita. */
    public record PrivateConversation(
            String conversationId,
            String otherUserId,
            String otherUserDisplayName,
            String otherUserAvatarUrl,
            String lastMessagePreview,
            String lastMessageAt
    ) {
    }

    /** Pagina de historial por cursor. {@code items} viene descendente (mas reciente primero). */
    public record PrivateMessageHistory(
            List<PrivateMessage> items,
            boolean hasMore,
            String nextBefore
    ) {
    }

    /** Exportacion de una conversacion para el usuario que la solicita, orden ascendente. */
    public record PrivateMessageExport(
            String conversationId,
            String exportedAt,
            int totalMessages,
            List<PrivateMessage> messages
    ) {
    }

    /** Envio de mensaje de texto. El cliente genera el {@code id} (UUID v4) para idempotencia. */
    public record SendPrivateMessageRequest(
            String id,
            String body
    ) {
    }

    /** Edicion de mensaje propio dentro de la ventana permitida por backend. */
    public record EditPrivateMessageRequest(
            String body
    ) {
    }

    /** Ping ligero del topic de bandeja: nunca lleva el cuerpo del mensaje. */
    public record PrivateInboxPing(
            String conversationId,
            String senderUserId,
            String sentAt
    ) {
    }
}
