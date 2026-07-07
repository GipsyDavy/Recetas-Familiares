package org.gipsybuho.recetasfamiliares.chat;

import java.util.List;

/**
 * Pagina de historial de chat por cursor. {@code items} viene descendente
 * (mas reciente primero). {@code nextBefore} es el id a pasar como
 * {@code before} para cargar la pagina anterior; null si no hay mas.
 */
public record ChatHistoryResponse(
        List<ChatMessageResponse> items,
        boolean hasMore,
        String nextBefore
) {
}
