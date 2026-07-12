package org.gipsybuho.recetasfamiliares.data.repository

import androidx.work.ListenableWorker.Result
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteDao
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeDao
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.MenuItemDao
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.local.RecipeDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity
import org.gipsybuho.recetasfamiliares.data.local.StockDao
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.FamilyNoteDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.FavoriteRecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.StockItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPullDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPushRequestDto
import org.gipsybuho.recetasfamiliares.sync.SyncWorkerRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncRepositoryE2eTest {

    private val api = mockk<RecetasApi>()
    private val database = mockk<RecetasDatabase>()
    private val sessionStore = mockk<SessionStore>()
    private lateinit var store: InMemorySyncStore
    private lateinit var repository: SyncRepository

    private var familyId: String? = FAMILY_ID
    private val familyIdFlow = MutableStateFlow<String?>(FAMILY_ID)
    private val lastSyncTimes = mutableMapOf<String, String?>()
    private val lastSyncTime: String? get() = lastSyncTimes[FAMILY_ID]

    @Before
    fun setUp() {
        store = InMemorySyncStore()
        store.attach(database)

        familyId = FAMILY_ID
        familyIdFlow.value = FAMILY_ID
        lastSyncTimes.clear()
        every { sessionStore.familyId } answers { familyId }
        every { sessionStore.userId } returns "user-e2e"
        every { sessionStore.familyIdFlow } returns familyIdFlow
        every { sessionStore.lastSyncTimeFor(any()) } answers { lastSyncTimes[firstArg()] }
        every { sessionStore.setLastSyncTime(any(), any()) } answers {
            lastSyncTimes[firstArg<String>()] = secondArg()
        }

        repository = SyncRepository(api, database, sessionStore)
    }

    @Test
    fun `pull paginado usa limit 200 y no avanza cursor si alcanza el tope`() = runTest {
        val limits = mutableListOf<Int?>()
        val cursors = mutableListOf<String?>()
        var call = 0
        coEvery { api.pullSync(FAMILY_ID, any(), any()) } answers {
            call++
            cursors += secondArg<String?>()
            limits += thirdArg<Int?>()
            emptyPull(serverTime = "T$call", hasMore = true, nextSince = "S$call")
        }

        repository.pullOnce()

        assertEquals(50, call)
        assertEquals(List(50) { 200 }, limits)
        assertEquals(null, cursors.first())
        assertEquals("S49", cursors.last())
        assertNull("No debe avanzar cursor si el servidor aun tiene paginas pendientes", lastSyncTime)
    }

    @Test
    fun `pushThenPull envia colas offline y aplica ACKs sin avanzar cursor hasta pull`() = runTest {
        store.recipes["r-new"] = recipeEntity("r-new", syncVersion = 0L, title = "Local new")
        store.recipes["r-edited"] = recipeEntity("r-edited", syncVersion = -7L, title = "Local edited")
        store.recipes["r-deleted"] = recipeEntity("r-deleted", syncVersion = -5L, deleted = true)
        store.ingredients["i-new"] = ingredientEntity("i-new", recipeId = "r-new", syncVersion = 0L)
        store.steps["st-new"] = stepEntity("st-new", recipeId = "r-new", syncVersion = 0L)
        store.stockItems["stock-new"] = stockEntity("stock-new", syncVersion = 0L)
        store.stockItems["stock-deleted"] = stockEntity("stock-deleted", syncVersion = -4L, deleted = true)
        store.notes["note-edited"] = noteEntity("note-edited", syncVersion = -6L, title = "Nota local")
        store.notes["note-deleted"] = noteEntity("note-deleted", syncVersion = -3L, deleted = true)
        store.favorites["fav-new"] = favoriteEntity("fav-new", syncVersion = 0L)
        store.favorites["fav-deleted"] = favoriteEntity("fav-deleted", syncVersion = -9L, deleted = true)
        store.shoppingItems["item-checked"] = shoppingItemEntity("item-checked", syncVersion = -2L)

        val pushed = slot<SyncPushRequestDto>()
        coEvery { api.pushSync(FAMILY_ID, capture(pushed)) } returns emptyPull(
            serverTime = "T-PUSH",
            recipes = listOf(
                recipeDto("r-new", title = "Server new", syncVersion = 11L),
                recipeDto("r-edited", title = "Server edited", syncVersion = 8L),
                recipeDto("r-deleted", title = "Server deleted", syncVersion = 6L, deleted = true)
            ),
            stockItems = listOf(stockDto("stock-new", syncVersion = 12L)),
            favoriteRecipes = listOf(favoriteDto("fav-new", syncVersion = 13L)),
            familyNotes = listOf(noteDto("note-edited", title = "Nota servidor", syncVersion = 14L))
        )
        coEvery { api.pullSync(FAMILY_ID, null, 200) } returns emptyPull(serverTime = "T-PULL")

        repository.pushThenPull()

        val recipesById = pushed.captured.recipes.associateBy { it.id }
        assertNull(recipesById.getValue("r-new").baseSyncVersion)
        assertEquals(7L, recipesById.getValue("r-edited").baseSyncVersion)
        assertEquals(5L, recipesById.getValue("r-deleted").baseSyncVersion)
        assertTrue(recipesById.getValue("r-deleted").deleted)
        assertEquals(listOf("i-new"), pushed.captured.ingredients.map { it.id })
        assertEquals(listOf("st-new"), pushed.captured.steps.map { it.id })

        val stockById = pushed.captured.stockItems!!.associateBy { it.id }
        assertNull(stockById.getValue("stock-new").baseSyncVersion)
        assertEquals(4L, stockById.getValue("stock-deleted").baseSyncVersion)
        assertTrue(stockById.getValue("stock-deleted").deleted)
        assertEquals(6L, pushed.captured.familyNotes!!.single { it.id == "note-edited" }.baseSyncVersion)
        assertEquals(3L, pushed.captured.familyNotes!!.single { it.id == "note-deleted" }.baseSyncVersion)
        assertEquals(9L, pushed.captured.favoriteRecipes!!.single { it.id == "fav-deleted" }.baseSyncVersion)
        assertEquals(2L, pushed.captured.shoppingListItems!!.single().baseSyncVersion)

        assertEquals("Server new", store.recipes.getValue("r-new").title)
        assertEquals(11L, store.recipes.getValue("r-new").syncVersion)
        assertEquals("Server edited", store.recipes.getValue("r-edited").title)
        assertTrue(store.recipes.getValue("r-deleted").deleted)
        assertEquals(12L, store.stockItems.getValue("stock-new").syncVersion)
        assertEquals("Nota servidor", store.notes.getValue("note-edited").title)
        assertEquals("T-PULL", lastSyncTime)
    }

    @Test
    fun `pushThenPull no envia pendientes de otra familia`() = runTest {
        store.recipes["r-active"] = recipeEntity("r-active", syncVersion = 0L, title = "Activa")
        store.recipes["r-other"] = recipeEntity(
            "r-other",
            syncVersion = 0L,
            title = "Otra",
            familyId = "fam-2"
        )
        store.stockItems["stock-active"] = stockEntity("stock-active", syncVersion = 0L)
        store.stockItems["stock-other"] = stockEntity("stock-other", syncVersion = 0L, familyId = "fam-2")
        store.notes["note-active"] = noteEntity("note-active", syncVersion = 0L)
        store.notes["note-other"] = noteEntity("note-other", syncVersion = 0L, familyId = "fam-2")
        store.favorites["fav-active"] = favoriteEntity("fav-active", syncVersion = 0L)
        store.favorites["fav-other"] = favoriteEntity("fav-other", syncVersion = 0L, familyId = "fam-2")

        val pushed = slot<SyncPushRequestDto>()
        coEvery { api.pushSync(FAMILY_ID, capture(pushed)) } returns emptyPull(serverTime = "T-PUSH")
        coEvery { api.pullSync(FAMILY_ID, null, 200) } returns emptyPull(serverTime = "T-PULL")

        repository.pushThenPull()

        assertEquals(listOf("r-active"), pushed.captured.recipes.map { it.id })
        assertEquals(listOf("stock-active"), pushed.captured.stockItems!!.map { it.id })
        assertEquals(listOf("note-active"), pushed.captured.familyNotes!!.map { it.id })
        assertEquals(listOf("fav-active"), pushed.captured.favoriteRecipes!!.map { it.id })
    }

    @Test
    fun `delete de entidades creadas offline elimina localmente sin API`() = runTest {
        store.recipes["r-new"] = recipeEntity("r-new", syncVersion = 0L)
        store.ingredients["i-new"] = ingredientEntity("i-new", recipeId = "r-new", syncVersion = 0L)
        store.steps["st-new"] = stepEntity("st-new", recipeId = "r-new", syncVersion = 0L)
        store.stockItems["stock-new"] = stockEntity("stock-new", syncVersion = 0L)
        store.notes["note-new"] = noteEntity("note-new", syncVersion = 0L)
        store.favorites["fav-new"] = favoriteEntity("fav-new", recipeId = "r-fav", syncVersion = 0L)

        RecipeRepository(api, database, sessionStore).delete(store.recipes.getValue("r-new"))
        StockRepository(api, store.stockDao, sessionStore).delete(store.stockItems.getValue("stock-new"))
        FamilyNoteRepository(api, database, sessionStore).delete(store.notes.getValue("note-new"))
        FavoriteRepository(api, database, sessionStore).toggle("r-fav")

        assertTrue(store.recipes.isEmpty())
        assertTrue(store.ingredients.isEmpty())
        assertTrue(store.steps.isEmpty())
        assertTrue(store.stockItems.isEmpty())
        assertTrue(store.notes.isEmpty())
        assertTrue(store.favorites.isEmpty())
        coVerify(exactly = 0) { api.deleteRecipe(any(), any()) }
        coVerify(exactly = 0) { api.deleteStockItem(any(), any()) }
        coVerify(exactly = 0) { api.deleteNote(any(), any()) }
        coVerify(exactly = 0) { api.removeFavorite(any(), any()) }
    }

    @Test
    fun `conflicto 409 en push hace pull y gana servidor`() = runTest {
        store.recipes["r-conflict"] = recipeEntity("r-conflict", syncVersion = -3L, title = "Local dirty")
        coEvery { api.pushSync(FAMILY_ID, any()) } throws http409()
        coEvery { api.pullSync(FAMILY_ID, null, 200) } returns emptyPull(
            serverTime = "T-SERVER",
            recipes = listOf(recipeDto("r-conflict", title = "Server winner", syncVersion = 4L))
        )

        repository.pushThenPull()

        assertEquals("Server winner", store.recipes.getValue("r-conflict").title)
        assertEquals(4L, store.recipes.getValue("r-conflict").syncVersion)
        assertEquals("T-SERVER", lastSyncTime)
        coVerify(exactly = 1) { api.pullSync(FAMILY_ID, null, 200) }
    }

    @Test
    fun `push 409 tras cambio de familia hace pull de la familia original sin tocar la nueva`() = runTest {
        store.recipes["r-a"] = recipeEntity("r-a", syncVersion = -3L, title = "Dirty A")
        store.recipes["r-b"] = recipeEntity("r-b", syncVersion = 0L, title = "Pendiente B", familyId = "fam-2")
        coEvery { api.pushSync(FAMILY_ID, any()) } answers {
            // El usuario cambia de familia con el push en vuelo
            familyId = "fam-2"
            familyIdFlow.value = "fam-2"
            throw http409()
        }
        coEvery { api.pullSync(FAMILY_ID, null, 200) } returns emptyPull(
            serverTime = "T-A",
            recipes = listOf(recipeDto("r-a", title = "Server A", syncVersion = 4L))
        )

        repository.pushThenPull()

        // El pull de resolucion pertenece a la familia original, nunca a la nueva
        coVerify(exactly = 1) { api.pullSync(FAMILY_ID, null, 200) }
        coVerify(exactly = 0) { api.pullSync("fam-2", any(), any()) }
        // El pendiente offline de la familia nueva queda intacto
        assertEquals(0L, store.recipes.getValue("r-b").syncVersion)
        assertEquals("Pendiente B", store.recipes.getValue("r-b").title)
        // El cursor avanzado es el de la familia original
        assertEquals("T-A", lastSyncTimes[FAMILY_ID])
        assertNull(lastSyncTimes["fam-2"])
    }

    @Test
    fun `CancellationException en push se relanza sin pull ni fallback`() = runTest {
        store.recipes["r-dirty"] = recipeEntity("r-dirty", syncVersion = -3L, title = "Local dirty")
        coEvery { api.pushSync(FAMILY_ID, any()) } throws CancellationException("cancelado")

        try {
            repository.pushThenPull()
            fail("Debe relanzar CancellationException")
        } catch (_: CancellationException) {
            // esperado
        }

        assertEquals("Local dirty", store.recipes.getValue("r-dirty").title)
        assertNull(lastSyncTime)
        coVerify(exactly = 0) { api.pullSync(any(), any(), any()) }
    }

    @Test
    fun `worker tras logout o cambio de servidor no empuja ni crashea`() = runTest {
        familyId = null
        store.stockItems["stock-pending"] = stockEntity("stock-pending", syncVersion = -2L)

        val result = SyncWorkerRunner.run {
            repository.pushThenPull()
        }

        assertEquals(Result.success(), result)
        assertNull(lastSyncTime)
        coVerify(exactly = 0) { api.pushSync(any(), any()) }
        coVerify(exactly = 0) { api.pullSync(any(), any(), any()) }
    }

    private class InMemorySyncStore {
        val recipes = linkedMapOf<String, RecipeEntity>()
        val ingredients = linkedMapOf<String, RecipeIngredientEntity>()
        val steps = linkedMapOf<String, RecipeStepEntity>()
        val stockItems = linkedMapOf<String, StockItemEntity>()
        val notes = linkedMapOf<String, FamilyNoteEntity>()
        val favorites = linkedMapOf<String, FavoriteRecipeEntity>()
        val shoppingItems = linkedMapOf<String, ShoppingListItemEntity>()

        val recipeDao = mockk<RecipeDao>()
        val ingredientDao = mockk<RecipeIngredientDao>()
        val stepDao = mockk<RecipeStepDao>()
        val stockDao = mockk<StockDao>()
        val menuItemDao = mockk<MenuItemDao>()
        val shoppingListDao = mockk<ShoppingListDao>()
        val shoppingListItemDao = mockk<ShoppingListItemDao>()
        val favoriteDao = mockk<FavoriteRecipeDao>()
        val noteDao = mockk<FamilyNoteDao>()
        val photoDao = mockk<RecipePhotoDao>()

        init {
            every { recipeDao.observeRecipes(any()) } returns emptyFlow()
            every { recipeDao.observeRecipe(any(), any()) } returns emptyFlow()
            coEvery { recipeDao.findAll(any()) } answers {
                val familyId = firstArg<String>()
                recipes.values.filter { it.familyId == familyId && !it.deleted }
            }
            coEvery { recipeDao.findPendingCreate(any()) } answers {
                val familyId = firstArg<String>()
                recipes.values.filter { it.familyId == familyId && it.syncVersion <= 0 && !it.deleted }
            }
            coEvery { recipeDao.findPendingDelete(any()) } answers {
                val familyId = firstArg<String>()
                recipes.values.filter { it.familyId == familyId && it.syncVersion <= 0 && it.deleted }
            }
            coEvery { recipeDao.findPendingIds(any()) } answers {
                val familyId = firstArg<String>()
                recipes.values.filter { it.familyId == familyId && it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { recipeDao.findByIdForFamily(any(), any()) } answers {
                val id = firstArg<String>()
                val familyId = secondArg<String>()
                recipes.values.firstOrNull { it.id == id && it.familyId == familyId && !it.deleted }
            }
            coEvery { recipeDao.deleteById(any()) } answers { recipes.remove(firstArg<String>()) }
            coEvery { recipeDao.upsertAll(any()) } answers { upsert(recipes, firstArg()) { it.id } }

            every { ingredientDao.observeIngredients(any(), any()) } returns emptyFlow()
            every { ingredientDao.observeAllIngredients(any()) } returns emptyFlow()
            coEvery { ingredientDao.findByRecipeIds(any()) } answers {
                val ids = firstArg<List<String>>().toSet()
                ingredients.values.filter { it.recipeId in ids && !it.deleted }
            }
            coEvery { ingredientDao.findPendingIds(any()) } answers {
                val familyId = firstArg<String>()
                val recipeIds = recipes.values.filter { it.familyId == familyId }.map { it.id }.toSet()
                ingredients.values.filter { it.recipeId in recipeIds && it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { ingredientDao.deleteByRecipeId(any()) } answers {
                val recipeId = firstArg<String>()
                ingredients.entries.removeIf { it.value.recipeId == recipeId }
            }
            coEvery { ingredientDao.upsertAll(any()) } answers { upsert(ingredients, firstArg()) { it.id } }

            every { stepDao.observeSteps(any(), any()) } returns emptyFlow()
            coEvery { stepDao.findByRecipeIds(any()) } answers {
                val ids = firstArg<List<String>>().toSet()
                steps.values.filter { it.recipeId in ids && !it.deleted }
            }
            coEvery { stepDao.findPendingIds(any()) } answers {
                val familyId = firstArg<String>()
                val recipeIds = recipes.values.filter { it.familyId == familyId }.map { it.id }.toSet()
                steps.values.filter { it.recipeId in recipeIds && it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { stepDao.deleteByRecipeId(any()) } answers {
                val recipeId = firstArg<String>()
                steps.entries.removeIf { it.value.recipeId == recipeId }
            }
            coEvery { stepDao.upsertAll(any()) } answers { upsert(steps, firstArg()) { it.id } }

            every { stockDao.observeStock(any()) } returns emptyFlow()
            coEvery { stockDao.findPendingCreate(any()) } answers {
                val familyId = firstArg<String>()
                stockItems.values.filter { it.familyId == familyId && it.syncVersion <= 0 && !it.deleted }
            }
            coEvery { stockDao.findPendingDelete(any()) } answers {
                val familyId = firstArg<String>()
                stockItems.values.filter { it.familyId == familyId && it.syncVersion <= 0 && it.deleted }
            }
            coEvery { stockDao.findPendingIds(any()) } answers {
                val familyId = firstArg<String>()
                stockItems.values.filter { it.familyId == familyId && it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { stockDao.deleteById(any()) } answers { stockItems.remove(firstArg<String>()) }
            coEvery { stockDao.findExpiringItems(any()) } returns emptyList()
            coEvery { stockDao.findCriticalItems(any(), any()) } returns emptyList()
            coEvery { stockDao.upsertAll(any()) } answers { upsert(stockItems, firstArg()) { it.id } }

            every { menuItemDao.observeMenuItems(any()) } returns emptyFlow()
            every { menuItemDao.observeMenuItemsFrom(any(), any()) } returns emptyFlow()
            coEvery { menuItemDao.upsertAll(any()) } just Runs

            every { shoppingListDao.observeShoppingLists(any()) } returns emptyFlow()
            coEvery { shoppingListDao.upsertAll(any()) } just Runs

            every { shoppingListItemDao.observeItems(any(), any()) } returns emptyFlow()
            coEvery { shoppingListItemDao.findPendingCheck(any()) } answers {
                shoppingItems.values.filter { it.syncVersion <= 0 && !it.deleted }
            }
            coEvery { shoppingListItemDao.findPendingIds(any()) } answers {
                shoppingItems.values.filter { it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { shoppingListItemDao.belongsToFamily(any(), any()) } returns true
            coEvery { shoppingListItemDao.upsertAll(any()) } answers { upsert(shoppingItems, firstArg()) { it.id } }

            every { favoriteDao.observeFavorites(any()) } returns emptyFlow()
            coEvery { favoriteDao.findByRecipeId(any(), any()) } answers {
                val recipeId = firstArg<String>()
                val familyId = secondArg<String>()
                favorites.values.firstOrNull { it.familyId == familyId && it.recipeId == recipeId && !it.deleted }
            }
            coEvery { favoriteDao.findPendingCreate(any()) } answers {
                val familyId = firstArg<String>()
                favorites.values.filter { it.familyId == familyId && it.syncVersion <= 0 && !it.deleted }
            }
            coEvery { favoriteDao.findPendingDelete(any()) } answers {
                val familyId = firstArg<String>()
                favorites.values.filter { it.familyId == familyId && it.syncVersion <= 0 && it.deleted }
            }
            coEvery { favoriteDao.findPendingIds(any()) } answers {
                val familyId = firstArg<String>()
                favorites.values.filter { it.familyId == familyId && it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { favoriteDao.deleteById(any()) } answers { favorites.remove(firstArg<String>()) }
            coEvery { favoriteDao.upsertAll(any()) } answers { upsert(favorites, firstArg()) { it.id } }

            every { noteDao.observeNotes(any()) } returns emptyFlow()
            coEvery { noteDao.findPendingCreate(any()) } answers {
                val familyId = firstArg<String>()
                notes.values.filter { it.familyId == familyId && it.syncVersion <= 0 && !it.deleted }
            }
            coEvery { noteDao.findPendingDelete(any()) } answers {
                val familyId = firstArg<String>()
                notes.values.filter { it.familyId == familyId && it.syncVersion <= 0 && it.deleted }
            }
            coEvery { noteDao.findPendingIds(any()) } answers {
                val familyId = firstArg<String>()
                notes.values.filter { it.familyId == familyId && it.syncVersion <= 0 }.map { it.id }
            }
            coEvery { noteDao.deleteById(any()) } answers { notes.remove(firstArg<String>()) }
            coEvery { noteDao.upsertAll(any()) } answers { upsert(notes, firstArg()) { it.id } }

            every { photoDao.observePhotos(any(), any()) } returns emptyFlow()
            coEvery { photoDao.findFirstByRecipeId(any(), any()) } returns null
            coEvery { photoDao.upsertAll(any()) } just Runs
        }

        fun attach(database: RecetasDatabase) {
            every { database.recipeDao() } returns recipeDao
            every { database.recipeIngredientDao() } returns ingredientDao
            every { database.recipeStepDao() } returns stepDao
            every { database.stockDao() } returns stockDao
            every { database.menuItemDao() } returns menuItemDao
            every { database.shoppingListDao() } returns shoppingListDao
            every { database.shoppingListItemDao() } returns shoppingListItemDao
            every { database.favoriteRecipeDao() } returns favoriteDao
            every { database.familyNoteDao() } returns noteDao
            every { database.recipePhotoDao() } returns photoDao
        }

        private fun <T> upsert(target: MutableMap<String, T>, values: List<T>, id: (T) -> String) {
            values.forEach { target[id(it)] = it }
        }
    }

    private companion object {
        const val FAMILY_ID = "fam-1"
        const val NOW = "2026-07-11T10:00:00Z"

        fun emptyPull(
            serverTime: String,
            recipes: List<RecipeDto>? = null,
            stockItems: List<StockItemDto>? = null,
            favoriteRecipes: List<FavoriteRecipeDto>? = null,
            familyNotes: List<FamilyNoteDto>? = null,
            hasMore: Boolean? = false,
            nextSince: String? = null
        ) = SyncPullDto(
            serverTime = serverTime,
            recipes = recipes,
            ingredients = null,
            steps = null,
            stockItems = stockItems,
            menuItems = null,
            shoppingLists = null,
            shoppingListItems = null,
            favoriteRecipes = favoriteRecipes,
            familyNotes = familyNotes,
            recipePhotos = null,
            hasMore = hasMore,
            nextSince = nextSince
        )

        fun recipeEntity(
            id: String,
            syncVersion: Long,
            title: String = "Recipe",
            deleted: Boolean = false,
            familyId: String = FAMILY_ID
        ) = RecipeEntity(
            id = id,
            familyId = familyId,
            title = title,
            description = null,
            servings = null,
            prepMinutes = null,
            cookMinutes = null,
            difficulty = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = deleted
        )

        fun recipeDto(
            id: String,
            title: String,
            syncVersion: Long,
            deleted: Boolean = false
        ) = RecipeDto(
            id = id,
            familyId = FAMILY_ID,
            title = title,
            description = null,
            servings = null,
            prepMinutes = null,
            cookMinutes = null,
            difficulty = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = deleted
        )

        fun ingredientEntity(id: String, recipeId: String, syncVersion: Long) = RecipeIngredientEntity(
            id = id,
            recipeId = recipeId,
            position = 1,
            name = "Harina",
            quantity = 1.0,
            unit = "kg",
            note = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = false
        )

        fun stepEntity(id: String, recipeId: String, syncVersion: Long) = RecipeStepEntity(
            id = id,
            recipeId = recipeId,
            position = 1,
            instruction = "Mezclar",
            timerMinutes = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = false
        )

        fun stockEntity(
            id: String,
            syncVersion: Long,
            deleted: Boolean = false,
            familyId: String = FAMILY_ID
        ) = StockItemEntity(
            id = id,
            familyId = familyId,
            name = "Harina",
            quantity = 1.0,
            unit = "kg",
            lowStockThreshold = null,
            expiresAt = null,
            note = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = deleted
        )

        fun stockDto(id: String, syncVersion: Long) = StockItemDto(
            id = id,
            familyId = FAMILY_ID,
            name = "Harina servidor",
            quantity = 2.0,
            unit = "kg",
            lowStockThreshold = null,
            expiresAt = null,
            note = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = false
        )

        fun noteEntity(
            id: String,
            syncVersion: Long,
            title: String = "Nota",
            deleted: Boolean = false,
            familyId: String = FAMILY_ID
        ) = FamilyNoteEntity(
            id = id,
            familyId = familyId,
            recipeId = null,
            recipeTitle = null,
            title = title,
            body = "Cuerpo",
            pinned = false,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = deleted
        )

        fun noteDto(id: String, title: String, syncVersion: Long) = FamilyNoteDto(
            id = id,
            familyId = FAMILY_ID,
            recipeId = null,
            recipeTitle = null,
            title = title,
            body = "Cuerpo servidor",
            pinned = false,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = false
        )

        fun favoriteEntity(
            id: String,
            syncVersion: Long,
            recipeId: String = "r-fav",
            deleted: Boolean = false,
            familyId: String = FAMILY_ID
        ) = FavoriteRecipeEntity(
            id = id,
            familyId = familyId,
            recipeId = recipeId,
            recipeTitle = "Tarta",
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = deleted
        )

        fun favoriteDto(id: String, syncVersion: Long) = FavoriteRecipeDto(
            id = id,
            familyId = FAMILY_ID,
            recipeId = "r-fav",
            recipeTitle = "Tarta servidor",
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = false
        )

        fun shoppingItemEntity(id: String, syncVersion: Long) = ShoppingListItemEntity(
            id = id,
            shoppingListId = "list-1",
            position = 1,
            name = "Pan",
            quantity = 1.0,
            unit = "ud",
            checked = true,
            note = null,
            createdAt = NOW,
            updatedAt = NOW,
            syncVersion = syncVersion,
            deleted = false
        )

        fun http409(): HttpException {
            val body = """{"message":"conflict"}""".toResponseBody("application/json".toMediaType())
            return HttpException(Response.error<Any>(409, body))
        }
    }
}
