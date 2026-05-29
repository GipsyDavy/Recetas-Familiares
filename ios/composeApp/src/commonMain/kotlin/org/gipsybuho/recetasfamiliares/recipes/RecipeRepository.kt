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
import org.gipsybuho.recetasfamiliares.network.FavoriteRecipeDto
import org.gipsybuho.recetasfamiliares.network.RecipeIngredientDto
import org.gipsybuho.recetasfamiliares.network.RecipeStepDto

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

    suspend fun loadIngredients(recipeId: String): List<RecipeIngredientDto> {
        val familyId = session.familyId ?: return emptyList()
        return try {
            apiClient.http.get("api/v1/families/$familyId/recipes/$recipeId/ingredients").body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadSteps(recipeId: String): List<RecipeStepDto> {
        val familyId = session.familyId ?: return emptyList()
        return try {
            apiClient.http.get("api/v1/families/$familyId/recipes/$recipeId/steps").body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadIsFavorite(recipeId: String): Boolean {
        val familyId = session.familyId ?: return false
        return try {
            val response: PageDto<FavoriteRecipeDto> = apiClient.http
                .get("api/v1/families/$familyId/favorites") { parameter("size", 200) }.body()
            response.items.any { it.recipeId == recipeId }
        } catch (e: Exception) { false }
    }

    suspend fun addFavorite(recipeId: String): Boolean {
        val familyId = session.familyId ?: return false
        return try { apiClient.http.post("api/v1/families/$familyId/recipes/$recipeId/favorites"); true }
        catch (e: Exception) { false }
    }

    suspend fun removeFavorite(recipeId: String): Boolean {
        val familyId = session.familyId ?: return false
        return try { apiClient.http.delete("api/v1/families/$familyId/recipes/$recipeId/favorites"); true }
        catch (e: Exception) { false }
    }

    fun loadLocalIngredients(): List<Pair<String, String>> =
        db.recipeIngredientsQueries.selectAllIngredients().executeAsList()
            .map { it.recipeId to it.name.lowercase().trim() }

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
