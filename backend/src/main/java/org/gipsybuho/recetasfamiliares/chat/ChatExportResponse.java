package org.gipsybuho.recetasfamiliares.chat;

import java.time.Instant;
import java.util.List;

/**
 * Exportacion del chat para el usuario que la solicita: refleja su vista actual
 * (respeta su marca de limpieza), en orden cronologico ascendente.
 */
public record ChatExportResponse(
        String familyId,
        Instant exportedAt,
        int totalMessages,
        List<ChatMessageResponse> messages
) {
}
