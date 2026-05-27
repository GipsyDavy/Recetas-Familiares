package org.gipsybuho.recetasfamiliares.data.repository

import kotlinx.coroutines.flow.Flow
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.RecipeDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.StockDao
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.LoginRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.StockItemDto

class AuthRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore
) {
    val isLoggedIn: Boolean
        get() = !sessionStore.accessToken.isNullOrBlank() && !sessionStore.familyId.isNullOrBlank()

    suspend fun login(email: String, password: String) {
        val response = api.login(LoginRequestDto(email.trim(), password))
        sessionStore.accessToken = response.accessToken
        sessionStore.refreshToken = response.refreshToken
        sessionStore.familyId = response.family.id
    }

    fun logout() {
        sessionStore.clear()
    }
}

class RecipeRepository(
    private val api: RecetasApi,
    private val recipeDao: RecipeDao,
    private val sessionStore: SessionStore
) {
    val recipes: Flow<List<RecipeEntity>> = recipeDao.observeRecipes()

    fun recipe(id: String): Flow<RecipeEntity?> = recipeDao.observeRecipe(id)

    suspend fun refresh() {
        val familyId = sessionStore.familyId ?: return
        val page = api.recipes(familyId)
        recipeDao.upsertAll(page.items.map { it.toEntity() })
    }
}

class StockRepository(
    private val api: RecetasApi,
    private val stockDao: StockDao,
    private val sessionStore: SessionStore
) {
    val stockItems: Flow<List<StockItemEntity>> = stockDao.observeStock()

    suspend fun refresh() {
        val familyId = sessionStore.familyId ?: return
        val page = api.stockItems(familyId)
        stockDao.upsertAll(page.items.map { it.toEntity() })
    }
}

class SyncRepository(
    private val api: RecetasApi,
    private val recipeDao: RecipeDao,
    private val stockDao: StockDao,
    private val sessionStore: SessionStore
) {
    suspend fun pullOnce() {
        val familyId = sessionStore.familyId ?: return
        val response = api.pullSync(familyId)
        recipeDao.upsertAll(response.recipes.map { it.toEntity() })
        stockDao.upsertAll(response.stockItems.map { it.toEntity() })
    }

    suspend fun pushThenPull() {
        val familyId = sessionStore.familyId ?: return
        val response = api.pushSync(familyId)
        recipeDao.upsertAll(response.recipes.map { it.toEntity() })
        stockDao.upsertAll(response.stockItems.map { it.toEntity() })
    }
}

private fun RecipeDto.toEntity() = RecipeEntity(
    id = id,
    familyId = familyId,
    title = title,
    description = description,
    servings = servings,
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    difficulty = difficulty,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
    deleted = deleted
)

private fun StockItemDto.toEntity() = StockItemEntity(
    id = id,
    familyId = familyId,
    name = name,
    quantity = quantity,
    unit = unit,
    lowStockThreshold = lowStockThreshold,
    expiresAt = expiresAt,
    note = note,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
    deleted = deleted
)
