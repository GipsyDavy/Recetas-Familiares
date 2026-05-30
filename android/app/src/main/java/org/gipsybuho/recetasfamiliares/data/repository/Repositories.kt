package org.gipsybuho.recetasfamiliares.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.MenuItemEntity
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
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
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateNoteRequestDto
import java.time.Instant
import java.util.UUID
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateRecipeRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateStockItemRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ReplaceIngredientsRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.ReplaceStepsRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateNoteRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateRecipeRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateStockItemRequestDto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.gipsybuho.recetasfamiliares.data.remote.dto.AssignMenuItemRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.CreateRatingRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeRatingDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateRatingRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncFamilyNotePushItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncFavoriteRecipePushItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncIngredientPushItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPushRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncRecipePushItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncShoppingListItemPushItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncStepPushItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncStockItemPushItemDto

class AuthRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore
) {
    val isLoggedIn: Boolean
        get() = !sessionStore.accessToken.isNullOrBlank() && !sessionStore.familyId.isNullOrBlank()

    suspend fun login(email: String, password: String) {
        val response = api.login(LoginRequestDto(email.trim(), password))
        sessionStore.accessToken  = response.accessToken
        sessionStore.refreshToken = response.refreshToken
        sessionStore.familyId     = response.family.id
        sessionStore.userId       = response.user.id
        sessionStore.displayName  = response.user.displayName
        sessionStore.email        = response.user.email
        try {
            val families = api.families()
            sessionStore.familyRole = families.firstOrNull { it.id == response.family.id }?.role
        } catch (_: Exception) { }
    }

    fun logout() {
        sessionStore.clear()
    }
}

class FamilyMemberRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore
) {
    suspend fun invite(email: String, role: String) =
        api.inviteMember(
            sessionStore.familyId ?: error("No family session"),
            org.gipsybuho.recetasfamiliares.data.remote.dto.InviteMemberRequestDto(email.trim(), role)
        )
}

class RecipeRepository(
    private val api: RecetasApi,
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    val recipes: Flow<List<RecipeEntity>> = database.recipeDao().observeRecipes()

    fun recipe(id: String): Flow<RecipeEntity?> = database.recipeDao().observeRecipe(id)

    private var _totalPages = 1
    val totalPages: Int get() = _totalPages

    suspend fun refresh() {
        val familyId = sessionStore.familyId ?: return
        val page = api.recipes(familyId, page = 0, size = 30)
        _totalPages = page.totalPages.coerceAtLeast(1)
        database.recipeDao().upsertAll(page.items.map { it.toEntity() })
    }

    suspend fun loadPage(pageNum: Int) {
        val familyId = sessionStore.familyId ?: return
        val page = api.recipes(familyId, page = pageNum, size = 30)
        _totalPages = page.totalPages.coerceAtLeast(1)
        database.recipeDao().upsertAll(page.items.map { it.toEntity() })
    }

    suspend fun create(
        title: String, description: String?, servings: Int?,
        prepMinutes: Int?, cookMinutes: Int?, difficulty: String?,
        ingredients: List<RecipeIngredientItemDto>, steps: List<RecipeStepItemDto>
    ) {
        val familyId = sessionStore.familyId ?: return
        try {
            val dto = api.createRecipe(familyId, CreateRecipeRequestDto(title, description, servings, prepMinutes, cookMinutes, difficulty))
            database.recipeDao().upsertAll(listOf(dto.toEntity()))
            if (ingredients.isNotEmpty()) {
                val ingDtos = api.replaceIngredients(familyId, dto.id, ReplaceIngredientsRequestDto(ingredients))
                database.recipeIngredientDao().upsertAll(ingDtos.map { it.toEntity() })
            }
            if (steps.isNotEmpty()) {
                val stepDtos = api.replaceSteps(familyId, dto.id, ReplaceStepsRequestDto(steps))
                database.recipeStepDao().upsertAll(stepDtos.map { it.toEntity() })
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: save full recipe locally with syncVersion=0 for SyncWorker to push later
            val now = Instant.now().toString()
            val recipeId = UUID.randomUUID().toString()
            database.recipeDao().upsertAll(listOf(
                RecipeEntity(recipeId, familyId, title, description, servings,
                    prepMinutes, cookMinutes, difficulty, now, now, 0L, false)
            ))
            if (ingredients.isNotEmpty()) {
                database.recipeIngredientDao().upsertAll(ingredients.mapIndexed { idx, ing ->
                    RecipeIngredientEntity(UUID.randomUUID().toString(), recipeId, idx + 1,
                        ing.name, ing.quantity, ing.unit, ing.note, now, now, 0L, false)
                })
            }
            if (steps.isNotEmpty()) {
                database.recipeStepDao().upsertAll(steps.mapIndexed { idx, step ->
                    RecipeStepEntity(UUID.randomUUID().toString(), recipeId, idx + 1,
                        step.instruction, step.timerMinutes, now, now, 0L, false)
                })
            }
        }
    }

    suspend fun update(
        recipe: RecipeEntity, title: String, description: String?, servings: Int?,
        prepMinutes: Int?, cookMinutes: Int?, difficulty: String?,
        ingredients: List<RecipeIngredientItemDto>, steps: List<RecipeStepItemDto>
    ) {
        val familyId = sessionStore.familyId ?: return
        try {
            val dto = api.updateRecipe(familyId, recipe.id, UpdateRecipeRequestDto(title, description, servings, prepMinutes, cookMinutes, difficulty))
            database.recipeDao().upsertAll(listOf(dto.toEntity()))
            val ingDtos = api.replaceIngredients(familyId, recipe.id, ReplaceIngredientsRequestDto(ingredients))
            database.recipeIngredientDao().upsertAll(ingDtos.map { it.toEntity() })
            val stepDtos = api.replaceSteps(familyId, recipe.id, ReplaceStepsRequestDto(steps))
            database.recipeStepDao().upsertAll(stepDtos.map { it.toEntity() })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: save recipe metadata locally; ingredients/steps unchanged until next sync
            val now = Instant.now().toString()
            database.recipeDao().upsertAll(listOf(
                recipe.copy(title = title, description = description, servings = servings,
                    prepMinutes = prepMinutes, cookMinutes = cookMinutes, difficulty = difficulty,
                    updatedAt = now, syncVersion = 0L)
            ))
        }
    }

    suspend fun delete(recipe: RecipeEntity) {
        val familyId = sessionStore.familyId ?: return
        try {
            api.deleteRecipe(familyId, recipe.id)
            database.recipeDao().upsertAll(listOf(recipe.copy(deleted = true)))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: mark deleted locally, SyncWorker will push on next sync
            database.recipeDao().upsertAll(listOf(recipe.copy(deleted = true, syncVersion = 0L)))
        }
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

    suspend fun create(
        name: String, quantity: Double?, unit: String?,
        lowStockThreshold: Double?, expiresAt: String?, note: String?
    ) {
        val familyId = sessionStore.familyId ?: return
        try {
            val dto = api.createStockItem(
                familyId, CreateStockItemRequestDto(name, quantity, unit, lowStockThreshold, expiresAt, note)
            )
            stockDao.upsertAll(listOf(dto.toEntity()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: save locally with syncVersion=0 for SyncWorker to push later
            val now = Instant.now().toString()
            stockDao.upsertAll(listOf(
                StockItemEntity(UUID.randomUUID().toString(), familyId, name, quantity, unit,
                    lowStockThreshold, expiresAt, note, now, now, 0L, false)
            ))
        }
    }

    suspend fun update(
        item: StockItemEntity, name: String, quantity: Double?, unit: String?,
        lowStockThreshold: Double?, expiresAt: String?, note: String?
    ) {
        val familyId = sessionStore.familyId ?: return
        try {
            val dto = api.updateStockItem(
                familyId, item.id, UpdateStockItemRequestDto(name, quantity, unit, lowStockThreshold, expiresAt, note)
            )
            stockDao.upsertAll(listOf(dto.toEntity()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: apply update locally, SyncWorker will push on next sync
            val now = Instant.now().toString()
            stockDao.upsertAll(listOf(
                item.copy(name = name, quantity = quantity, unit = unit,
                    lowStockThreshold = lowStockThreshold, expiresAt = expiresAt,
                    note = note, updatedAt = now, syncVersion = 0L)
            ))
        }
    }

    suspend fun delete(item: StockItemEntity) {
        val familyId = sessionStore.familyId ?: return
        try {
            api.deleteStockItem(familyId, item.id)
            stockDao.upsertAll(listOf(item.copy(deleted = true)))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: mark deleted locally, SyncWorker will push on next sync
            stockDao.upsertAll(listOf(item.copy(deleted = true, syncVersion = 0L)))
        }
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

        val pendingRecipes        = database.recipeDao().findPendingCreate()
        val pendingRecipeDel      = database.recipeDao().findPendingDelete()
        val pendingStock          = database.stockDao().findPendingCreate()
        val pendingStockDelete    = database.stockDao().findPendingDelete()
        val pendingNotes          = database.familyNoteDao().findPendingCreate()
        val pendingNoteDelete     = database.familyNoteDao().findPendingDelete()
        val pendingShoppingItems  = database.shoppingListItemDao().findPendingCheck()
        val pendingFavorites      = database.favoriteRecipeDao().findPendingCreate()
        val pendingFavoriteDel    = database.favoriteRecipeDao().findPendingDelete()

        val recipeIds = pendingRecipes.map { it.id }
        val pendingIngredients = if (recipeIds.isNotEmpty())
            database.recipeIngredientDao().findByRecipeIds(recipeIds) else emptyList()
        val pendingSteps = if (recipeIds.isNotEmpty())
            database.recipeStepDao().findByRecipeIds(recipeIds) else emptyList()

        val recipePushItems = pendingRecipes.map {
            SyncRecipePushItemDto(id = it.id, baseSyncVersion = null,
                title = it.title, description = it.description, servings = it.servings,
                prepMinutes = it.prepMinutes, cookMinutes = it.cookMinutes,
                difficulty = it.difficulty, deleted = false)
        } + pendingRecipeDel.map {
            SyncRecipePushItemDto(id = it.id, baseSyncVersion = null, deleted = true)
        }

        val ingredientPushItems = pendingIngredients.map {
            SyncIngredientPushItemDto(id = it.id, baseSyncVersion = null, recipeId = it.recipeId,
                position = it.position, name = it.name, quantity = it.quantity,
                unit = it.unit, note = it.note, deleted = false)
        }

        val stepPushItems = pendingSteps.map {
            SyncStepPushItemDto(id = it.id, baseSyncVersion = null, recipeId = it.recipeId,
                position = it.position, instruction = it.instruction,
                timerMinutes = it.timerMinutes, deleted = false)
        }

        val stockPushItems = pendingStock.map {
            SyncStockItemPushItemDto(id = it.id, baseSyncVersion = null,
                name = it.name, quantity = it.quantity, unit = it.unit,
                lowStockThreshold = it.lowStockThreshold, expiresAt = it.expiresAt,
                note = it.note, deleted = false)
        } + pendingStockDelete.map {
            SyncStockItemPushItemDto(id = it.id, baseSyncVersion = null, deleted = true)
        }

        val notePushItems = pendingNotes.map {
            SyncFamilyNotePushItemDto(id = it.id, baseSyncVersion = null,
                recipeId = it.recipeId, title = it.title, body = it.body,
                pinned = it.pinned, deleted = false)
        } + pendingNoteDelete.map {
            SyncFamilyNotePushItemDto(id = it.id, baseSyncVersion = null, deleted = true)
        }

        val shoppingItemPushItems = pendingShoppingItems.map {
            SyncShoppingListItemPushItemDto(
                id = it.id, baseSyncVersion = null, shoppingListId = it.shoppingListId,
                position = it.position, name = it.name, quantity = it.quantity,
                unit = it.unit, checked = it.checked, note = it.note, deleted = false
            )
        }

        val favoritePushItems = pendingFavorites.map {
            SyncFavoriteRecipePushItemDto(id = it.id, baseSyncVersion = null, recipeId = it.recipeId, deleted = false)
        } + pendingFavoriteDel.map {
            SyncFavoriteRecipePushItemDto(id = it.id, baseSyncVersion = null, deleted = true)
        }

        val pushRequest = SyncPushRequestDto(
            recipes = recipePushItems,
            ingredients = ingredientPushItems,
            steps = stepPushItems,
            stockItems = stockPushItems.ifEmpty { null },
            shoppingListItems = shoppingItemPushItems.ifEmpty { null },
            favoriteRecipes = favoritePushItems.ifEmpty { null },
            familyNotes = notePushItems.ifEmpty { null }
        )

        val response = api.pushSync(familyId, pushRequest)

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
        try {
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: save checked state locally, SyncWorker will push later
            database.shoppingListItemDao().upsertAll(listOf(item.copy(checked = checked, syncVersion = 0L)))
        }
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
            try {
                api.removeFavorite(familyId, existing.id)
                database.favoriteRecipeDao().upsertAll(listOf(existing.copy(deleted = true)))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Offline: mark deleted locally, SyncWorker will push on next sync
                database.favoriteRecipeDao().upsertAll(listOf(existing.copy(deleted = true, syncVersion = 0L)))
            }
        } else {
            try {
                val dto = api.addFavorite(familyId, AddFavoriteRequestDto(recipeId))
                database.favoriteRecipeDao().upsertAll(listOf(dto.toEntity()))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Offline: save locally with syncVersion=0 for SyncWorker to push later
                val now = Instant.now().toString()
                database.favoriteRecipeDao().upsertAll(listOf(
                    FavoriteRecipeEntity(UUID.randomUUID().toString(), familyId, recipeId, null, now, now, 0L, false)
                ))
            }
        }
    }
}

class FamilyNoteRepository(
    private val api: RecetasApi,
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    val notes: Flow<List<FamilyNoteEntity>> = database.familyNoteDao().observeNotes()

    suspend fun create(title: String, body: String, pinned: Boolean) {
        val familyId = sessionStore.familyId ?: return
        try {
            val dto = api.createNote(familyId, CreateNoteRequestDto(title, body, pinned))
            database.familyNoteDao().upsertAll(listOf(dto.toEntity()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: save locally with syncVersion=0 for SyncWorker to push later
            val now = Instant.now().toString()
            database.familyNoteDao().upsertAll(listOf(
                FamilyNoteEntity(UUID.randomUUID().toString(), familyId, null, null,
                    title, body, pinned, now, now, 0L, false)
            ))
        }
    }

    suspend fun update(note: FamilyNoteEntity, title: String, body: String, pinned: Boolean) {
        val familyId = sessionStore.familyId ?: return
        try {
            val dto = api.updateNote(familyId, note.id, UpdateNoteRequestDto(title, body, pinned))
            database.familyNoteDao().upsertAll(listOf(dto.toEntity()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: apply update locally, SyncWorker will push on next sync
            val now = Instant.now().toString()
            database.familyNoteDao().upsertAll(listOf(
                note.copy(title = title, body = body, pinned = pinned, updatedAt = now, syncVersion = 0L)
            ))
        }
    }

    suspend fun delete(note: FamilyNoteEntity) {
        val familyId = sessionStore.familyId ?: return
        try {
            api.deleteNote(familyId, note.id)
            database.familyNoteDao().upsertAll(listOf(note.copy(deleted = true)))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline: mark deleted locally, SyncWorker will push on next sync
            database.familyNoteDao().upsertAll(listOf(note.copy(deleted = true, syncVersion = 0L)))
        }
    }
}

class MenuItemRepository(
    private val api: RecetasApi,
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    suspend fun assign(recipeId: String, plannedDate: String, mealType: String) {
        val familyId = sessionStore.familyId ?: return
        val dto = api.assignMenuItem(familyId, AssignMenuItemRequestDto(recipeId, plannedDate, mealType))
        database.menuItemDao().upsertAll(listOf(dto.toEntity()))
    }

    suspend fun remove(item: MenuItemEntity) {
        val familyId = sessionStore.familyId ?: return
        api.removeMenuItem(familyId, item.id)
        database.menuItemDao().upsertAll(listOf(item.copy(deleted = true)))
    }
}

class RecipeRatingRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore
) {
    suspend fun loadRatings(recipeId: String): List<RecipeRatingDto> {
        val familyId = sessionStore.familyId ?: return emptyList()
        return api.ratings(familyId, recipeId)
    }

    suspend fun create(recipeId: String, stars: Int, comment: String?): RecipeRatingDto {
        val familyId = sessionStore.familyId ?: error("Not logged in")
        return api.createRating(familyId, recipeId, CreateRatingRequestDto(stars, comment))
    }

    suspend fun update(recipeId: String, ratingId: String, stars: Int, comment: String?): RecipeRatingDto {
        val familyId = sessionStore.familyId ?: error("Not logged in")
        return api.updateRating(familyId, recipeId, ratingId, UpdateRatingRequestDto(stars, comment))
    }

    suspend fun delete(recipeId: String, ratingId: String) {
        val familyId = sessionStore.familyId ?: return
        api.deleteRating(familyId, recipeId, ratingId)
    }
}

class RecipePhotoRepository(
    private val api: RecetasApi,
    private val database: RecetasDatabase,
    private val sessionStore: SessionStore
) {
    fun photosFor(recipeId: String): Flow<List<RecipePhotoEntity>> =
        database.recipePhotoDao().observePhotos(recipeId)

    suspend fun loadPhotos(recipeId: String) {
        val familyId = sessionStore.familyId ?: return
        val dtos = api.photos(familyId, recipeId)
        database.recipePhotoDao().upsertAll(dtos.filter { !it.deleted }.map { it.toEntity() })
    }

    suspend fun upload(recipeId: String, bytes: ByteArray, contentType: String, caption: String?) {
        val familyId = sessionStore.familyId ?: return
        val requestFile = bytes.toRequestBody(contentType.toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", "photo.jpg", requestFile)
        val captionPart = caption?.toRequestBody("text/plain".toMediaType())
        val dto = api.uploadPhoto(familyId, recipeId, filePart, captionPart)
        database.recipePhotoDao().upsertAll(listOf(dto.toEntity()))
    }

    suspend fun delete(photo: RecipePhotoEntity) {
        val familyId = sessionStore.familyId ?: return
        api.deletePhoto(familyId, photo.recipeId, photo.id)
        database.recipePhotoDao().upsertAll(listOf(photo.copy(deleted = true)))
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
