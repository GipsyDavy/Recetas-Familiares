package org.gipsybuho.recetasfamiliares.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.MainDispatcherRule
import org.gipsybuho.recetasfamiliares.core.AppContainer
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoDao
import org.gipsybuho.recetasfamiliares.data.local.RecipePhotoEntity
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository
import org.gipsybuho.recetasfamiliares.data.repository.RecipeRepository
import org.gipsybuho.recetasfamiliares.data.repository.StockRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Estado observable del ViewModel: aislamiento entre familias, filtro por stock,
 * paginacion y sesion invalidada. Todo en JVM, con dobles del contenedor.
 *
 * Los StateFlow usan SharingStarted.WhileSubscribed, asi que cada test tiene que
 * suscribirse (backgroundScope) antes de leer .value: sin suscriptor el flujo
 * upstream no se colecta y el valor se queda en el inicial.
 */
class RecetasViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val container = mockk<AppContainer>(relaxed = true)
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val database = mockk<RecetasDatabase>(relaxed = true)
    private val recipeRepository = mockk<RecipeRepository>(relaxed = true)
    private val stockRepository = mockk<StockRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val photoDao = mockk<RecipePhotoDao>(relaxed = true)
    private val ingredientDao = mockk<RecipeIngredientDao>(relaxed = true)

    private val familyIdFlow = MutableStateFlow<String?>(FAMILY_A)

    private fun buildViewModel(
        recipes: List<RecipeEntity> = emptyList(),
        stock: List<StockItemEntity> = emptyList(),
        ingredients: List<RecipeIngredientEntity> = emptyList(),
        loggedIn: Boolean = true
    ): RecetasViewModel {
        every { container.sessionStore } returns sessionStore
        every { container.database } returns database
        every { container.recipeRepository } returns recipeRepository
        every { container.stockRepository } returns stockRepository
        every { container.authRepository } returns authRepository

        every { sessionStore.familyIdFlow } returns familyIdFlow
        every { sessionStore.familyId } returns familyIdFlow.value
        every { sessionStore.familyRoleFlow } returns MutableStateFlow("OWNER")

        every { database.recipePhotoDao() } returns photoDao
        every { database.recipeIngredientDao() } returns ingredientDao
        every { photoDao.observeCovers(any()) } returns emptyFlow()
        every { ingredientDao.observeAllIngredients(any()) } returns flowOf(ingredients)

        every { recipeRepository.recipes } returns flowOf(recipes)
        every { stockRepository.stockItems } returns flowOf(stock)
        every { authRepository.isLoggedIn } returns loggedIn

        return RecetasViewModel(container)
    }

    // --- Aislamiento entre familias ---

    @Test
    fun `las portadas se rehacen al cambiar de familia y no arrastran las anteriores`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = buildViewModel()
            // Despues de construir: buildViewModel deja un stub por defecto con
            // any(), y en MockK gana el ultimo registrado. observeCovers no se
            // invoca hasta que alguien colecta, asi que llegamos a tiempo.
            every { photoDao.observeCovers(FAMILY_A) } returns
                flowOf(listOf(photo("p1", "receta-a")))
            every { photoDao.observeCovers(FAMILY_B) } returns
                flowOf(listOf(photo("p2", "receta-b")))

            backgroundScope.launch { viewModel.recipeCovers.collect {} }
            advanceUntilIdle()

            assertEquals(setOf("receta-a"), viewModel.recipeCovers.value.keys)

            familyIdFlow.value = FAMILY_B
            advanceUntilIdle()

            assertEquals(
                "Las portadas de la familia anterior no pueden sobrevivir al cambio",
                setOf("receta-b"),
                viewModel.recipeCovers.value.keys
            )
        }

    @Test
    fun `sin familia activa no hay portadas`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = buildViewModel()
        every { photoDao.observeCovers(FAMILY_A) } returns flowOf(listOf(photo("p1", "receta-a")))

        backgroundScope.launch { viewModel.recipeCovers.collect {} }
        advanceUntilIdle()
        assertEquals(setOf("receta-a"), viewModel.recipeCovers.value.keys)

        familyIdFlow.value = null
        advanceUntilIdle()

        assertTrue(viewModel.recipeCovers.value.isEmpty())
    }

    // --- Filtro por stock ---

    @Test
    fun `con el filtro apagado se ven todas las recetas`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = buildViewModel(recipes = listOf(recipe("r1", "Cocido"), recipe("r2", "Flan")))
        backgroundScope.launch { viewModel.filteredRecipes.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("r1", "r2"), viewModel.filteredRecipes.value.map { it.id })
    }

    @Test
    fun `el filtro deja solo las recetas cuyos ingredientes estan en stock`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = buildViewModel(
                recipes = listOf(recipe("r1", "Cocido"), recipe("r2", "Flan")),
                stock = listOf(stockItem("s1", "Garbanzos")),
                ingredients = listOf(ingredient("i1", "r1", "Garbanzos"), ingredient("i2", "r2", "Huevos"))
            )
            backgroundScope.launch { viewModel.filteredRecipes.collect {} }
            advanceUntilIdle()

            viewModel.toggleStockFilter()
            advanceUntilIdle()

            assertEquals(listOf("r1"), viewModel.filteredRecipes.value.map { it.id })
        }

    @Test
    fun `el filtro compara ignorando mayusculas y espacios`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = buildViewModel(
            recipes = listOf(recipe("r1", "Cocido")),
            stock = listOf(stockItem("s1", "  GARBANZOS  ")),
            ingredients = listOf(ingredient("i1", "r1", "garbanzos"))
        )
        backgroundScope.launch { viewModel.filteredRecipes.collect {} }
        advanceUntilIdle()

        viewModel.toggleStockFilter()
        advanceUntilIdle()

        assertEquals(listOf("r1"), viewModel.filteredRecipes.value.map { it.id })
    }

    @Test
    fun `con el stock vacio el filtro no esconde nada`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = buildViewModel(
            recipes = listOf(recipe("r1", "Cocido")),
            stock = emptyList(),
            ingredients = listOf(ingredient("i1", "r1", "Garbanzos"))
        )
        backgroundScope.launch { viewModel.filteredRecipes.collect {} }
        advanceUntilIdle()

        viewModel.toggleStockFilter()
        advanceUntilIdle()

        assertEquals(
            "Filtrar con la despensa vacia dejaria la pantalla en blanco sin explicacion",
            listOf("r1"),
            viewModel.filteredRecipes.value.map { it.id }
        )
    }

    // --- Paginacion ---

    @Test
    fun `no pide otra pagina cuando no quedan mas`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = buildViewModel()

        viewModel.loadNextRecipePage()
        advanceUntilIdle()

        coVerify(exactly = 0) { recipeRepository.loadPage(any()) }
    }

    @Test
    fun `tras refrescar con varias paginas se puede pedir la siguiente`() =
        runTest(mainDispatcherRule.dispatcher) {
            every { recipeRepository.totalPages } returns 3
            coEvery { recipeRepository.refresh() } returns Unit

            val viewModel = buildViewModel()
            backgroundScope.launch { viewModel.recipeHasMore.collect {} }

            viewModel.refresh()
            advanceUntilIdle()
            assertTrue(viewModel.recipeHasMore.value)

            viewModel.loadNextRecipePage()
            advanceUntilIdle()

            coVerify(exactly = 1) { recipeRepository.loadPage(1) }
        }

    @Test
    fun `al agotar las paginas deja de ofrecer mas`() = runTest(mainDispatcherRule.dispatcher) {
        every { recipeRepository.totalPages } returns 2
        coEvery { recipeRepository.refresh() } returns Unit

        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.recipeHasMore.collect {} }

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.loadNextRecipePage()
        advanceUntilIdle()

        assertFalse(viewModel.recipeHasMore.value)
    }

    // --- Sesion invalidada ---

    @Test
    fun `si la sesion se limpia por debajo el ViewModel vuelve a login`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = buildViewModel(loggedIn = true)
            backgroundScope.launch { viewModel.isLoggedIn.collect {} }
            advanceUntilIdle()
            assertTrue(viewModel.isLoggedIn.value)

            // El authenticator revoca la sesion: limpia el familyId y deja de
            // estar logueado. La UI no puede quedarse mostrando datos vacios.
            every { authRepository.isLoggedIn } returns false
            familyIdFlow.value = null
            advanceUntilIdle()

            assertFalse(viewModel.isLoggedIn.value)
        }

    // --- Perfil tras iniciar sesion ---

    @Test
    fun `tras iniciar sesion el perfil muestra el usuario sin reiniciar la aplicacion`() =
        runTest(mainDispatcherRule.dispatcher) {
            // El ViewModel se construye en la pantalla de login, con la sesion aun
            // vacia: es ahi donde _displayName y _email toman su valor inicial.
            every { sessionStore.displayName } returns null
            every { sessionStore.email } returns null

            val viewModel = buildViewModel(loggedIn = false)
            assertNull(viewModel.displayName.value)
            assertNull(viewModel.email.value)

            // AuthRepository.login persiste el usuario en la sesion (Repositories.kt).
            coEvery { authRepository.login(any(), any()) } answers {
                every { sessionStore.displayName } returns "Emma"
                every { sessionStore.email } returns "emma@example.test"
                Unit
            }

            viewModel.login("emma@example.test", "clave-de-prueba") {}
            advanceUntilIdle()

            assertEquals(
                "El perfil no puede quedarse vacio hasta que se reinicie la aplicacion",
                "Emma",
                viewModel.displayName.value
            )
            assertEquals("emma@example.test", viewModel.email.value)
        }

    @Test
    fun `un cambio de familia normal no cierra la sesion`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = buildViewModel(loggedIn = true)
        backgroundScope.launch { viewModel.isLoggedIn.collect {} }
        advanceUntilIdle()

        familyIdFlow.value = FAMILY_B
        advanceUntilIdle()

        assertTrue(viewModel.isLoggedIn.value)
    }

    private fun recipe(id: String, title: String) = RecipeEntity(
        id = id,
        familyId = FAMILY_A,
        title = title,
        description = null,
        servings = null,
        prepMinutes = null,
        cookMinutes = null,
        difficulty = null,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        syncVersion = 1L,
        deleted = false
    )

    private fun ingredient(id: String, recipeId: String, name: String) = RecipeIngredientEntity(
        id = id,
        recipeId = recipeId,
        position = 0,
        name = name,
        quantity = null,
        unit = null,
        note = null,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        syncVersion = 1L,
        deleted = false
    )

    private fun stockItem(id: String, name: String) = StockItemEntity(
        id = id,
        familyId = FAMILY_A,
        name = name,
        quantity = 1.0,
        unit = "kg",
        lowStockThreshold = null,
        expiresAt = null,
        note = null,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        syncVersion = 1L,
        deleted = false
    )

    private fun photo(id: String, recipeId: String) = RecipePhotoEntity(
        id = id,
        recipeId = recipeId,
        position = 0,
        url = "https://api.example.test/uploads/$id.jpg",
        thumbnailUrl = "https://api.example.test/uploads/thumbs/$id.jpg",
        caption = null,
        contentType = "image/jpeg",
        sizeBytes = 1_000L,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        syncVersion = 1L,
        deleted = false
    )

    private companion object {
        const val FAMILY_A = "fam-a"
        const val FAMILY_B = "fam-b"
        const val CREATED_AT = "2026-08-05T10:00:00Z"
    }
}
