package org.gipsybuho.recetasfamiliares.core

import org.gipsybuho.recetasfamiliares.network.StockItemDto

/**
 * Programa notificaciones locales de caducidad de stock.
 * Llamar tras cada carga/sync de stock — es idempotente.
 */
expect class ExpiryNotificationScheduler() {
    fun schedule(items: List<StockItemDto>)
}
