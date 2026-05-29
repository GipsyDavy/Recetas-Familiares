package org.gipsybuho.recetasfamiliares.recipes

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.AppDatabase
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.database.Recipes
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.PageDto
import org.gipsybuho.recetasfamiliares.network.RecipeDto

class RecipeRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore,
    driverFactory: DatabaseDriverFactory
) {
    private val db = AppDatabase(driverFactory.createDriver())

    suspend fun loadRecipes(page: Int = 0, size: Int = 30): List<RecipeDto> {
        val familyId = session.familyId ?: return emptyList()
        return try {
            val response: PageDto<RecipeDto> = apiClient.http
                .get("api/v1/families/$familyId/recipes") {
                    parameter("page", page)
                    parameter("size", size)
                }.body()
            response.items.filter { !it.deleted }.also { items ->
                items.forEach { dto ->
                    db.recipesQueries.insertOrReplaceRecipe(
                        id          = dto.id,
                        familyId    = dto.familyId,
                        title       = dto.title,
                        description = dto.description,
                        difficulty  = dto.difficulty,
                        servings    = dto.servings?.toLong(),
                        prepMinutes = dto.prepMinutes?.toLong(),
                        cookMinutes = dto.cookMinutes?.toLong(),
                        updatedAt   = dto.updatedAt,
                        syncVersion = dto.syncVersion,
                        deleted     = 0L
                    )
                }
            }
        } catch (e: Exception) {
            db.recipesQueries.selectAllRecipes().executeAsList().map { it.toDto() }
        }
    }

    private fun Recipes.toDto() = RecipeDto(
        id          = id,
        familyId    = familyId,
        title       = title,
        description = description,
        servings    = servings?.toInt(),
        prepMinutes = prepMinutes?.toInt(),
        cookMinutes = cookMinutes?.toInt(),
        difficulty  = difficulty,
        createdAt   = updatedAt,
        updatedAt   = updatedAt,
        syncVersion = syncVersion,
        deleted     = deleted != 0L
    )
}
