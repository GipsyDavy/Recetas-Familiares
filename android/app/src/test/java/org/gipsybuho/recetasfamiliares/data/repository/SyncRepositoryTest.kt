package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemDao
import org.gipsybuho.recetasfamiliares.data.local.ShoppingListItemEntity
import org.gipsybuho.recetasfamiliares.data.local.StockDao
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
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

        coEvery { recipeDao.findPendingIds(FAMILY_ID) } returns emptyList()
        coEvery { ingredientDao.findPendingIds(FAMILY_ID) } returns emptyList()
        coEvery { stepDao.findPendingIds(FAMILY_ID) } returns emptyList()
        coEvery { stockDao.findPendingIds(FAMILY_ID) } returns emptyList()
        coEvery { shoppingListItemDao.findPendingIds(FAMILY_ID) } returns emptyList()
        coEvery { favoriteDao.findPendingIds(FAMILY_ID) } returns emptyList()
        coEvery { noteDao.findPendingIds(FAMILY_ID) } returns emptyList()

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
        every { sessionStore.userId } returns USER_ID
        every { sessionStore.familyIdFlow } returns MutableStateFlow(FAMILY_ID)
        every { sessionStore.lastSyncTimeFor(FAMILY_ID) } returns null
        every { sessionStore.setLastSyncTime(FAMILY_ID, any()) } answers { savedLastSyncTime = secondArg() }

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
    fun `pull en vuelo no aplica datos si la sesion del usuario murio`() = runTest {
        // Primera lectura de userId captura el dueño del sync; la segunda
        // (guard post-respuesta) simula un logout en vuelo devolviendo null.
        var reads = 0
        every { sessionStore.userId } answers { if (reads++ == 0) USER_ID else null }
        coEvery { api.pullSync(FAMILY_ID, null, any()) } returns emptyPull(
            serverTime = "T1",
            recipes = listOf(recipeDto("r-1"))
        )

        repository.pullOnce()

        coVerify(exactly = 0) { recipeDao.upsertAll(any()) }
        assertNull("el cursor no debe avanzar tras un logout en vuelo", savedLastSyncTime)
    }

    @Test
    fun `pullOnce no pisa filas locales pendientes de sincronizar`() = runTest {
        coEvery { recipeDao.findPendingIds(FAMILY_ID) } returns listOf("r-dirty")
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
        coEvery { recipeDao.findPendingCreate(FAMILY_ID) } returns listOf(
            recipeEntity("r-new", syncVersion = 0L),
            recipeEntity("r-edited", syncVersion = -3L)
        )
        coEvery { recipeDao.findPendingDelete(FAMILY_ID) } returns listOf(
            recipeEntity("r-deleted", syncVersion = -2L, deleted = true)
        )
        coEvery { ingredientDao.findByRecipeIds(any()) } returns emptyList()
        coEvery { stepDao.findByRecipeIds(any()) } returns emptyList()
        coEvery { stockDao.findPendingCreate(FAMILY_ID) } returns emptyList()
        coEvery { stockDao.findPendingDelete(FAMILY_ID) } returns emptyList()
        coEvery { noteDao.findPendingCreate(FAMILY_ID) } returns emptyList()
        coEvery { noteDao.findPendingDelete(FAMILY_ID) } returns emptyList()
        coEvery { shoppingListItemDao.findPendingCheck(FAMILY_ID) } returns emptyList()
        coEvery { favoriteDao.findPendingCreate(FAMILY_ID) } returns emptyList()
        coEvery { favoriteDao.findPendingDelete(FAMILY_ID) } returns emptyList()

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

    @Test
    fun `pushThenPull envia colas offline de stock notas favoritos y compra`() = runTest {
        coEvery { recipeDao.findPendingCreate(FAMILY_ID) } returns emptyList()
        coEvery { recipeDao.findPendingDelete(FAMILY_ID) } returns emptyList()
        coEvery { stockDao.findPendingCreate(FAMILY_ID) } returns listOf(stockEntity("s-new", syncVersion = 0L))
        coEvery { stockDao.findPendingDelete(FAMILY_ID) } returns listOf(stockEntity("s-deleted", syncVersion = -8L, deleted = true))
        coEvery { noteDao.findPendingCreate(FAMILY_ID) } returns listOf(noteEntity("n-edited", syncVersion = -3L))
        coEvery { noteDao.findPendingDelete(FAMILY_ID) } returns listOf(noteEntity("n-deleted", syncVersion = -4L, deleted = true))
        coEvery { shoppingListItemDao.findPendingCheck(FAMILY_ID) } returns listOf(shoppingItemEntity("li-checked", syncVersion = -5L))
        coEvery { favoriteDao.findPendingCreate(FAMILY_ID) } returns listOf(favoriteEntity("f-new", syncVersion = 0L))
        coEvery { favoriteDao.findPendingDelete(FAMILY_ID) } returns listOf(favoriteEntity("f-deleted", syncVersion = -6L, deleted = true))

        val pushed = slot<SyncPushRequestDto>()
        coEvery { api.pushSync(FAMILY_ID, capture(pushed)) } returns emptyPull(serverTime = "T-PUSH")
        coEvery { api.pullSync(FAMILY_ID, null, any()) } returns emptyPull(serverTime = "T-PULL")

        repository.pushThenPull()

        val stockById = pushed.captured.stockItems!!.associateBy { it.id }
        assertNull(stockById.getValue("s-new").baseSyncVersion)
        assertEquals(false, stockById.getValue("s-new").deleted)
        assertEquals(8L, stockById.getValue("s-deleted").baseSyncVersion)
        assertEquals(true, stockById.getValue("s-deleted").deleted)

        val notesById = pushed.captured.familyNotes!!.associateBy { it.id }
        assertEquals(3L, notesById.getValue("n-edited").baseSyncVersion)
        assertEquals("Nota", notesById.getValue("n-edited").title)
        assertEquals(false, notesById.getValue("n-edited").deleted)
        assertEquals(4L, notesById.getValue("n-deleted").baseSyncVersion)
        assertEquals(true, notesById.getValue("n-deleted").deleted)

        val shoppingItem = pushed.captured.shoppingListItems!!.single()
        assertEquals("li-checked", shoppingItem.id)
        assertEquals(5L, shoppingItem.baseSyncVersion)
        assertEquals(true, shoppingItem.checked)

        val favoritesById = pushed.captured.favoriteRecipes!!.associateBy { it.id }
        assertNull(favoritesById.getValue("f-new").baseSyncVersion)
        assertEquals("r-fav", favoritesById.getValue("f-new").recipeId)
        assertEquals(false, favoritesById.getValue("f-new").deleted)
        assertEquals(6L, favoritesById.getValue("f-deleted").baseSyncVersion)
        assertEquals(true, favoritesById.getValue("f-deleted").deleted)
        assertEquals("T-PULL", savedLastSyncTime)
    }

    private companion object {
        const val FAMILY_ID = "fam-1"
        const val USER_ID = "user-1"

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

        fun stockEntity(id: String, syncVersion: Long, deleted: Boolean = false) = StockItemEntity(
            id = id, familyId = FAMILY_ID, name = "Harina", quantity = 1.0, unit = "kg",
            lowStockThreshold = null, expiresAt = null, note = null,
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion, deleted = deleted
        )

        fun noteEntity(id: String, syncVersion: Long, deleted: Boolean = false) = FamilyNoteEntity(
            id = id, familyId = FAMILY_ID, recipeId = null, recipeTitle = null,
            title = "Nota", body = "Cuerpo", pinned = true,
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion, deleted = deleted
        )

        fun shoppingItemEntity(id: String, syncVersion: Long) = ShoppingListItemEntity(
            id = id, shoppingListId = "list-1", position = 1, name = "Pan",
            quantity = 1.0, unit = "ud", checked = true, note = null,
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion, deleted = false
        )

        fun favoriteEntity(id: String, syncVersion: Long, deleted: Boolean = false) = FavoriteRecipeEntity(
            id = id, familyId = FAMILY_ID, recipeId = "r-fav", recipeTitle = "Tarta",
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion, deleted = deleted
        )
    }
}
