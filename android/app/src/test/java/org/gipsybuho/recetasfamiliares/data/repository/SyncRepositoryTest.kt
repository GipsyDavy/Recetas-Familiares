package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteDao
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeDao
import org.gipsybuho.recetasfamiliares.data.local.MenuItemDao
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.local.RecipeDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientDao
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemDao
import org.gipsybuho.recetasfamiliares.data.local.StockDao
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPullDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.SyncPushRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncRepositoryTest {

    private val api = mockk<RecetasApi>()
    private val database = mockk<RecetasDatabase>()
    private val sessionStore = mockk<SessionStore>()

    private val recipeDao = mockk<RecipeDao>()
    private val ingredientDao = mockk<RecipeIngredientDao>()
    private val stepDao = mockk<RecipeStepDao>()
    private val stockDao = mockk<StockDao>()
    private val menuItemDao = mockk<MenuItemDao>()
    private val shoppingListDao = mockk<ShoppingListDao>()
    private val shoppingListItemDao = mockk<ShoppingListItemDao>()
    private val favoriteDao = mockk<FavoriteRecipeDao>()
    private val noteDao = mockk<FamilyNoteDao>()
    private val photoDao = mockk<RecipePhotoDao>()

    private lateinit var repository: SyncRepository
    private var savedLastSyncTime: String? = null

    @Before
    fun setUp() {
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

        coEvery { recipeDao.findPendingIds() } returns emptyList()
        coEvery { ingredientDao.findPendingIds() } returns emptyList()
        coEvery { stepDao.findPendingIds() } returns emptyList()
        coEvery { stockDao.findPendingIds() } returns emptyList()
        coEvery { shoppingListItemDao.findPendingIds() } returns emptyList()
        coEvery { favoriteDao.findPendingIds() } returns emptyList()
        coEvery { noteDao.findPendingIds() } returns emptyList()

        coEvery { recipeDao.upsertAll(any()) } just Runs
        coEvery { ingredientDao.upsertAll(any()) } just Runs
        coEvery { stepDao.upsertAll(any()) } just Runs
        coEvery { stockDao.upsertAll(any()) } just Runs
        coEvery { menuItemDao.upsertAll(any()) } just Runs
        coEvery { shoppingListDao.upsertAll(any()) } just Runs
        coEvery { shoppingListItemDao.upsertAll(any()) } just Runs
        coEvery { favoriteDao.upsertAll(any()) } just Runs
        coEvery { noteDao.upsertAll(any()) } just Runs
        coEvery { photoDao.upsertAll(any()) } just Runs

        savedLastSyncTime = null
        every { sessionStore.familyId } returns FAMILY_ID
        every { sessionStore.lastSyncTime } returns null
        every { sessionStore.lastSyncTime = any() } answers { savedLastSyncTime = firstArg() }

        repository = SyncRepository(api, database, sessionStore)
    }

    @Test
    fun `pullOnce con una sola pagina avanza lastSyncTime al serverTime`() = runTest {
        coEvery { api.pullSync(FAMILY_ID, null, any()) } returns
            emptyPull(serverTime = "2026-07-05T10:00:00Z")

        repository.pullOnce()

        assertEquals("2026-07-05T10:00:00Z", savedLastSyncTime)
        coVerify(exactly = 1) { api.pullSync(FAMILY_ID, null, any()) }
    }

    @Test
    fun `pullOnce multipagina encadena nextSince y avanza al completar`() = runTest {
        coEvery { api.pullSync(FAMILY_ID, null, any()) } returns
            emptyPull(serverTime = "T1", hasMore = true, nextSince = "S1")
        coEvery { api.pullSync(FAMILY_ID, "S1", any()) } returns
            emptyPull(serverTime = "T2", hasMore = false)

        repository.pullOnce()

        assertEquals("T2", savedLastSyncTime)
        coVerify(exactly = 1) { api.pullSync(FAMILY_ID, null, any()) }
        coVerify(exactly = 1) { api.pullSync(FAMILY_ID, "S1", any()) }
    }

    @Test
    fun `pullOnce corta en el tope de paginas sin avanzar lastSyncTime`() = runTest {
        // Servidor patologico: siempre hasMore=true con nextSince nuevo
        var call = 0
        coEvery { api.pullSync(FAMILY_ID, any(), any()) } answers {
            call++
            emptyPull(serverTime = "T$call", hasMore = true, nextSince = "S$call")
        }

        repository.pullOnce()

        assertNull("lastSyncTime no debe avanzar si quedan paginas pendientes", savedLastSyncTime)
        coVerify(exactly = 50) { api.pullSync(FAMILY_ID, any(), any()) }
    }

    @Test
    fun `pullOnce no pisa filas locales pendientes de sincronizar`() = runTest {
        coEvery { recipeDao.findPendingIds() } returns listOf("r-dirty")
        coEvery { api.pullSync(FAMILY_ID, null, any()) } returns emptyPull(
            serverTime = "T1",
            recipes = listOf(recipeDto("r-dirty"), recipeDto("r-clean"))
        )
        val upserted = slot<List<RecipeEntity>>()
        coEvery { recipeDao.upsertAll(capture(upserted)) } just Runs

        repository.pullOnce()

        assertEquals(listOf("r-clean"), upserted.captured.map { it.id })
    }

    @Test
    fun `pushThenPull envia baseSyncVersion segun la convencion de syncVersion`() = runTest {
        // syncVersion=0 creado offline; syncVersion=-3 editado offline (base 3);
        // syncVersion=-2 + deleted borrado offline (base 2)
        coEvery { recipeDao.findPendingCreate() } returns listOf(
            recipeEntity("r-new", syncVersion = 0L),
            recipeEntity("r-edited", syncVersion = -3L)
        )
        coEvery { recipeDao.findPendingDelete() } returns listOf(
            recipeEntity("r-deleted", syncVersion = -2L, deleted = true)
        )
        coEvery { ingredientDao.findByRecipeIds(any()) } returns emptyList()
        coEvery { stepDao.findByRecipeIds(any()) } returns emptyList()
        coEvery { stockDao.findPendingCreate() } returns emptyList()
        coEvery { stockDao.findPendingDelete() } returns emptyList()
        coEvery { noteDao.findPendingCreate() } returns emptyList()
        coEvery { noteDao.findPendingDelete() } returns emptyList()
        coEvery { shoppingListItemDao.findPendingCheck() } returns emptyList()
        coEvery { favoriteDao.findPendingCreate() } returns emptyList()
        coEvery { favoriteDao.findPendingDelete() } returns emptyList()

        val pushed = slot<SyncPushRequestDto>()
        coEvery { api.pushSync(FAMILY_ID, capture(pushed)) } returns
            emptyPull(serverTime = "T-PUSH")
        coEvery { api.pullSync(FAMILY_ID, null, any()) } returns
            emptyPull(serverTime = "T-PULL")

        repository.pushThenPull()

        val byId = pushed.captured.recipes.associateBy { it.id }
        assertNull("creado offline no tiene version base", byId.getValue("r-new").baseSyncVersion)
        assertEquals(3L, byId.getValue("r-edited").baseSyncVersion)
        assertEquals(false, byId.getValue("r-edited").deleted)
        assertEquals(2L, byId.getValue("r-deleted").baseSyncVersion)
        assertEquals(true, byId.getValue("r-deleted").deleted)
        assertTrue(pushed.captured.ingredients.isEmpty())
        // El cursor NO debe avanzar con el serverTime del push (solo trae ACKs de lo
        // empujado); avanza con el pull posterior, que si trae cambios de otros miembros.
        assertEquals("T-PULL", savedLastSyncTime)
        coVerify(exactly = 1) { api.pullSync(FAMILY_ID, null, any()) }
    }

    private companion object {
        const val FAMILY_ID = "fam-1"

        fun emptyPull(
            serverTime: String,
            hasMore: Boolean? = null,
            nextSince: String? = null,
            recipes: List<RecipeDto> = emptyList()
        ) = SyncPullDto(
            serverTime = serverTime, recipes = recipes, ingredients = null, steps = null,
            stockItems = null, menuItems = null, shoppingLists = null, shoppingListItems = null,
            favoriteRecipes = null, familyNotes = null, recipePhotos = null,
            hasMore = hasMore, nextSince = nextSince
        )

        fun recipeDto(id: String) = RecipeDto(
            id = id, familyId = FAMILY_ID, title = "t", description = null, servings = null,
            prepMinutes = null, cookMinutes = null, difficulty = null,
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = 1L, deleted = false
        )

        fun recipeEntity(id: String, syncVersion: Long, deleted: Boolean = false) = RecipeEntity(
            id = id, familyId = FAMILY_ID, title = "t", description = null, servings = null,
            prepMinutes = null, cookMinutes = null, difficulty = null,
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion, deleted = deleted
        )
    }
}
