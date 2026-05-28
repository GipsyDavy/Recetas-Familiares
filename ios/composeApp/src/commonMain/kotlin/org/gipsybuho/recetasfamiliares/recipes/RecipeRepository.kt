package org.gipsybuho.recetasfamiliares.recipes

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.PageDto
import org.gipsybuho.recetasfamiliares.network.RecipeDto

class RecipeRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun loadRecipes(page: Int = 0, size: Int = 30): List<RecipeDto> {
        val familyId = session.familyId ?: return emptyList()
        val response: PageDto<RecipeDto> = apiClient.http
            .get("api/v1/families/$familyId/recipes") {
                parameter("page", page)
                parameter("size", size)
            }.body()
        return response.items.filter { !it.deleted }
    }
}
