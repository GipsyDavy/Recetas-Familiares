package org.gipsybuho.recetasfamiliares.stock

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.AppDatabase
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.database.Stock_items
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.PageDto
import org.gipsybuho.recetasfamiliares.network.StockItemDto

class StockRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore,
    driverFactory: DatabaseDriverFactory
) {
    private val db = AppDatabase(driverFactory.createDriver())

    suspend fun loadStockItems(page: Int = 0, size: Int = 50): List<StockItemDto> {
        val familyId = session.familyId ?: return emptyList()
        return try {
            val response: PageDto<StockItemDto> = apiClient.http
                .get("api/v1/families/$familyId/stock-items") {
                    parameter("page", page)
                    parameter("size", size)
                }.body()
            response.items.filter { !it.deleted }.also { items ->
                items.forEach { dto ->
                    db.stockItemsQueries.insertOrReplaceStockItem(
                        id                = dto.id,
                        familyId          = dto.familyId,
                        name              = dto.name,
                        quantity          = dto.quantity,
                        unit              = dto.unit,
                        expiresAt         = dto.expiresAt,
                        lowStockThreshold = dto.lowStockThreshold,
                        note              = dto.note,
                        updatedAt         = dto.updatedAt,
                        syncVersion       = dto.syncVersion,
                        deleted           = 0L
                    )
                }
            }
        } catch (e: Exception) {
            db.stockItemsQueries.selectAllStockItems().executeAsList().map { it.toDto() }
        }
    }

    private fun Stock_items.toDto() = StockItemDto(
        id                = id,
        familyId          = familyId,
        name              = name,
        quantity          = quantity,
        unit              = unit,
        expiresAt         = expiresAt,
        lowStockThreshold = lowStockThreshold,
        note              = note,
        createdAt         = updatedAt,
        updatedAt         = updatedAt,
        syncVersion       = syncVersion,
        deleted           = deleted != 0L
    )
}
