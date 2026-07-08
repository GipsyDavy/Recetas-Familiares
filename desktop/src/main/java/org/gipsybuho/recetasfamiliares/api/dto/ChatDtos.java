package org.gipsybuho.recetasfamiliares.api.dto;

import java.util.List;

/**
 * DTOs del chat familiar (fase 1). Reflejan el contrato REST/WS del backend
 * bajo {@code /api/v1/families/{familyId}/chat}. Las marcas de tiempo se
 * modelan como String ISO-8601 (UTC) para deserializar sin adaptadores Gson.
 */
public final class ChatDtos {

    private ChatDtos() {}

    /**
     * Mensaje de chat. {@code body} es null cuando el mensaje esta borrado.
     * Entidad sincronizable estandar: id, createdAt, updatedAt, syncVersion, deleted.
     */
    public record ChatAttachment(
            String id,
            String url,
            String thumbnailUrl,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {
    }

    public record ChatMessage(
            String id,
            String familyId,
            String authorUserId,
            String authorDisplayName,
            String body,
            List<ChatAttachment> attachments,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {
        public List<ChatAttachment> attachmentsOrEmpty() {
            return attachments != null ? attachments : List.of();
        }

        /** Descarta frames WS incompletos o mal formados antes de mostrarlos. */
        public boolean isUsable() {
            return notBlank(id)
                    && notBlank(familyId)
                    && notBlank(authorUserId)
                    && notBlank(authorDisplayName)
                    && notBlank(createdAt);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** Pagina de historial por cursor. {@code items} viene descendente (mas reciente primero). */
    public record ChatHistory(
            List<ChatMessage> items,
            boolean hasMore,
            String nextBefore
    ) {
    }

    /** Exportacion del chat para el usuario que la solicita, en orden ascendente. */
    public record ChatExport(
            String familyId,
            String exportedAt,
            int totalMessages,
            List<ChatMessage> messages
    ) {
    }

    /** Envio de mensaje de texto. El cliente genera el {@code id} (UUID v4) para idempotencia. */
    public record SendChatMessageRequest(
            String id,
            String body
    ) {
    }
}
