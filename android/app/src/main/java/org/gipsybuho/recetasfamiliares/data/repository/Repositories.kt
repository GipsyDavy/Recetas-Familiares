package org.gipsybuho.recetasfamiliares.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.local.RecipeDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity
import org.gipsybuho.recetasfamiliares.data.local.StockDao
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyNoteDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AddFavoriteRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FavoriteRecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.LoginRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.MenuItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipePhotoDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ShoppingListDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ShoppingListItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateShoppingListItemRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.StockItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPushRequestDto

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
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    suspend fun pullOnce() {
        val familyId = sessionStore.familyId ?: return
        val response = api.pullSync(familyId, sessionStore.lastSyncTime)

        database.recipeDao().upsertAll(response.recipes.orEmpty().map { it.toEntity() })
        database.recipeIngredientDao().upsertAll(response.ingredients.orEmpty().map { it.toEntity() })
        database.recipeStepDao().upsertAll(response.steps.orEmpty().map { it.toEntity() })
        database.stockDao().upsertAll(response.stockItems.orEmpty().map { it.toEntity() })
        database.menuItemDao().upsertAll(response.menuItems.orEmpty().map { it.toEntity() })
        database.shoppingListDao().upsertAll(response.shoppingLists.orEmpty().map { it.toEntity() })
        database.shoppingListItemDao().upsertAll(response.shoppingListItems.orEmpty().map { it.toEntity() })
        database.favoriteRecipeDao().upsertAll(response.favoriteRecipes.orEmpty().map { it.toEntity() })
        database.familyNoteDao().upsertAll(response.familyNotes.orEmpty().map { it.toEntity() })
        database.recipePhotoDao().upsertAll(response.recipePhotos.orEmpty().map { it.toEntity() })

        sessionStore.lastSyncTime = response.serverTime
    }

    suspend fun pushThenPull() {
        val familyId = sessionStore.familyId ?: return
        val response = api.pushSync(familyId, SyncPushRequestDto())

        database.recipeDao().upsertAll(response.recipes.orEmpty().map { it.toEntity() })
        database.recipeIngredientDao().upsertAll(response.ingredients.orEmpty().map { it.toEntity() })
        database.recipeStepDao().upsertAll(response.steps.orEmpty().map { it.toEntity() })
        database.stockDao().upsertAll(response.stockItems.orEmpty().map { it.toEntity() })
        database.menuItemDao().upsertAll(response.menuItems.orEmpty().map { it.toEntity() })
        database.shoppingListDao().upsertAll(response.shoppingLists.orEmpty().map { it.toEntity() })
        database.shoppingListItemDao().upsertAll(response.shoppingListItems.orEmpty().map { it.toEntity() })
        database.favoriteRecipeDao().upsertAll(response.favoriteRecipes.orEmpty().map { it.toEntity() })
        database.familyNoteDao().upsertAll(response.familyNotes.orEmpty().map { it.toEntity() })
        database.recipePhotoDao().upsertAll(response.recipePhotos.orEmpty().map { it.toEntity() })

        sessionStore.lastSyncTime = response.serverTime
    }
}

class ShoppingListRepository(
    private val api: RecetasApi,
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    val shoppingLists: Flow<List<ShoppingListEntity>> = database.shoppingListDao().observeShoppingLists()

    fun itemsFor(listId: String): Flow<List<ShoppingListItemEntity>> =
        database.shoppingListItemDao().observeItems(listId)

    suspend fun checkItem(item: ShoppingListItemEntity, checked: Boolean) {
        val familyId = sessionStore.familyId ?: return
        val req = UpdateShoppingListItemRequestDto(
            position = item.position,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            checked = checked,
            note = item.note
        )
        val updated = api.updateShoppingListItem(familyId, item.shoppingListId, item.id, req)
        database.shoppingListItemDao().upsertAll(listOf(updated.toEntity()))
    }
}

class FavoriteRepository(
    private val api: RecetasApi,
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    fun isFavorite(recipeId: String): Flow<Boolean> =
        database.favoriteRecipeDao().observeFavorites()
            .map { list -> list.any { it.recipeId == recipeId } }

    suspend fun toggle(recipeId: String) {
        val familyId = sessionStore.familyId ?: return
        val existing = database.favoriteRecipeDao().findByRecipeId(recipeId)
        if (existing != null) {
            api.removeFavorite(familyId, existing.id)
            database.favoriteRecipeDao().upsertAll(listOf(existing.copy(deleted = true)))
        } else {
            val dto = api.addFavorite(familyId, AddFavoriteRequestDto(recipeId))
            database.favoriteRecipeDao().upsertAll(listOf(dto.toEntity()))
        }
    }
}

// ── Entity mappers ─────────────────────────────────────────────────────────────

private fun RecipeDto.toEntity() = RecipeEntity(
    id = id, familyId = familyId, title = title, description = description,
    servings = servings, prepMinutes = prepMinutes, cookMinutes = cookMinutes,
    difficulty = difficulty, createdAt = createdAt, updatedAt = updatedAt,
    syncVersion = syncVersion, deleted = deleted
)

private fun RecipeIngredientDto.toEntity() = RecipeIngredientEntity(
    id = id, recipeId = recipeId, position = position, name = name,
    quantity = quantity, unit = unit, note = note, createdAt = createdAt,
    updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun RecipeStepDto.toEntity() = RecipeStepEntity(
    id = id, recipeId = recipeId, position = position, instruction = instruction,
    timerMinutes = timerMinutes, createdAt = createdAt, updatedAt = updatedAt,
    syncVersion = syncVersion, deleted = deleted
)

private fun StockItemDto.toEntity() = StockItemEntity(
    id = id, familyId = familyId, name = name, quantity = quantity, unit = unit,
    lowStockThreshold = lowStockThreshold, expiresAt = expiresAt, note = note,
    createdAt = createdAt, updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun MenuItemDto.toEntity() = MenuItemEntity(
    id = id, familyId = familyId, recipeId = recipeId, recipeTitle = recipeTitle,
    plannedDate = plannedDate, mealType = mealType, note = note,
    createdAt = createdAt, updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun ShoppingListDto.toEntity() = ShoppingListEntity(
    id = id, familyId = familyId, name = name, plannedFrom = plannedFrom,
    plannedTo = plannedTo, note = note, completed = completed,
    createdAt = createdAt, updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun ShoppingListItemDto.toEntity() = ShoppingListItemEntity(
    id = id, shoppingListId = shoppingListId, position = position, name = name,
    quantity = quantity, unit = unit, checked = checked, note = note,
    createdAt = createdAt, updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun FavoriteRecipeDto.toEntity() = FavoriteRecipeEntity(
    id = id, familyId = familyId, recipeId = recipeId, recipeTitle = recipeTitle,
    createdAt = createdAt, updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun FamilyNoteDto.toEntity() = FamilyNoteEntity(
    id = id, familyId = familyId, recipeId = recipeId, recipeTitle = recipeTitle,
    title = title, body = body, pinned = pinned,
    createdAt = createdAt, updatedAt = updatedAt, syncVersion = syncVersion, deleted = deleted
)

private fun RecipePhotoDto.toEntity() = RecipePhotoEntity(
    id = id, recipeId = recipeId, position = position, url = url,
    thumbnailUrl = thumbnailUrl, caption = caption, contentType = contentType,
    sizeBytes = sizeBytes, createdAt = createdAt, updatedAt = updatedAt,
    syncVersion = syncVersion, deleted = deleted
)
