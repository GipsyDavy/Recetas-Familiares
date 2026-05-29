package org.gipsybuho.recetasfamiliares.menu

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.MenuItemDto
import org.gipsybuho.recetasfamiliares.network.PageDto

class MenuRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun loadAllItems(): List<MenuItemDto> {
        val familyId = session.familyId ?: return emptyList()
        val response: PageDto<MenuItemDto> = apiClient.http
            .get("api/v1/families/$familyId/menu-items") {
                parameter("page", 0)
                parameter("size", 200)
            }.body()
        return response.items.filter { !it.deleted }.sortedWith(
            compareBy({ it.plannedDate }, { mealTypeOrder(it.mealType) })
        )
    }

    private fun mealTypeOrder(type: String) = when (type) {
        "BREAKFAST" -> 0
        "LUNCH"     -> 1
        "SNACK"     -> 2
        "DINNER"    -> 3
        else        -> 4
    }
}
