package org.gipsybuho.recetasfamiliares.stock

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.AppDatabase
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.database.Stock_items
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.CreateStockItemRequest
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

    suspend fun createStockItem(
        name: String, quantity: Double?, unit: String?,
        expiresAt: String?, lowStockThreshold: Double?, note: String?
    ): StockItemDto {
        val familyId = session.familyId ?: error("Sin sesión activa")
        return apiClient.http.post("api/v1/families/$familyId/stock-items") {
            setBody(CreateStockItemRequest(name, quantity, unit, expiresAt, lowStockThreshold, note))
        }.body()
    }

    suspend fun updateStockItem(
        id: String, name: String, quantity: Double?, unit: String?,
        expiresAt: String?, lowStockThreshold: Double?, note: String?
    ): StockItemDto {
        val familyId = session.familyId ?: error("Sin sesión activa")
        return apiClient.http.put("api/v1/families/$familyId/stock-items/$id") {
            setBody(CreateStockItemRequest(name, quantity, unit, expiresAt, lowStockThreshold, note))
        }.body()
    }

    suspend fun deleteStockItem(id: String) {
        val familyId = session.familyId ?: error("Sin sesión activa")
        apiClient.http.delete("api/v1/families/$familyId/stock-items/$id")
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
