package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;

/**
 * Fila de la bandeja de conversaciones: la conversacion vista desde la
 * perspectiva del usuario que la solicita (otherUser* siempre es el otro
 * participante, nunca el propio usuario autenticado).
 */
public record PrivateConversationResponse(
        String conversationId,
        String otherUserId,
        String otherUserDisplayName,
        String otherUserAvatarUrl,
        String lastMessagePreview,
        Instant lastMessageAt
) {
}
