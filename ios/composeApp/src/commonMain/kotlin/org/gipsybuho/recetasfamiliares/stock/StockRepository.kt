package org.gipsybuho.recetasfamiliares.stock

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.PageDto
import org.gipsybuho.recetasfamiliares.network.StockItemDto

class StockRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun loadStockItems(page: Int = 0, size: Int = 50): List<StockItemDto> {
        val familyId = session.familyId ?: return emptyList()
        val response: PageDto<StockItemDto> = apiClient.http
            .get("api/v1/families/$familyId/stock-items") {
                parameter("page", page)
                parameter("size", size)
            }.body()
        return response.items.filter { !it.deleted }
    }
}
