package org.gipsybuho.recetasfamiliares.shopping

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.PageDto
import org.gipsybuho.recetasfamiliares.network.ShoppingListDto
import org.gipsybuho.recetasfamiliares.network.ShoppingListItemDto

class ShoppingListRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun loadLists(page: Int = 0, size: Int = 20): List<ShoppingListDto> {
        val familyId = session.familyId ?: return emptyList()
        val response: PageDto<ShoppingListDto> = apiClient.http
            .get("api/v1/families/$familyId/shopping-lists") {
                parameter("page", page)
                parameter("size", size)
            }.body()
        return response.items.filter { !it.deleted }
    }

    suspend fun loadItems(listId: String, page: Int = 0, size: Int = 100): List<ShoppingListItemDto> {
        val familyId = session.familyId ?: return emptyList()
        val response: PageDto<ShoppingListItemDto> = apiClient.http
            .get("api/v1/families/$familyId/shopping-lists/$listId/items") {
                parameter("page", page)
                parameter("size", size)
            }.body()
        return response.items.filter { !it.deleted }.sortedBy { it.position }
    }
}
