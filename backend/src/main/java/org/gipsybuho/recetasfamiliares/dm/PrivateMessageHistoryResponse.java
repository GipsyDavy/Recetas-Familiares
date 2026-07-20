package org.gipsybuho.recetasfamiliares.dm;

import java.util.List;

/**
 * Pagina de historial por cursor. {@code items} viene descendente (mas
 * reciente primero). {@code nextBefore} es el id a pasar como {@code before}
 * para cargar la pagina anterior; null si no hay mas.
 */
public record PrivateMessageHistoryResponse(
        List<PrivateMessageResponse> items,
        boolean hasMore,
        String nextBefore
) {
}
