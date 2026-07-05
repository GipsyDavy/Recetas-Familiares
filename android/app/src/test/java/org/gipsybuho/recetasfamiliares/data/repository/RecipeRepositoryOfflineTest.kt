package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.local.RecipeDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeIngredientEntity
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepDao
import org.gipsybuho.recetasfamiliares.data.local.RecipeStepEntity
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeIngredientItemDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.RecipeStepItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Amplia COD-8 sobre la convencion offline de recetas:
 * creado offline = syncVersion 0; editado/borrado offline conserva la base en negativo.
 */
class RecipeRepositoryOfflineTest {

    private val api = mockk<RecetasApi>()
    private val database = mockk<RecetasDatabase>()
    private val sessionStore = mockk<SessionStore>()
    private val recipeDao = mockk<RecipeDao>()
    private val ingredientDao = mockk<RecipeIngredientDao>()
    private val stepDao = mockk<RecipeStepDao>()

    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        every { sessionStore.familyId } returns FAMILY_ID
        every { database.recipeDao() } returns recipeDao
        every { database.recipeIngredientDao() } returns ingredientDao
        every { database.recipeStepDao() } returns stepDao
        every { recipeDao.observeRecipes() } returns emptyFlow()

        coEvery { recipeDao.upsertAll(any()) } just Runs
        coEvery { ingredientDao.upsertAll(any()) } just Runs
        coEvery { stepDao.upsertAll(any()) } just Runs
        coEvery { recipeDao.deleteById(any()) } just Runs
        coEvery { ingredientDao.deleteByRecipeId(any()) } just Runs
        coEvery { stepDao.deleteByRecipeId(any()) } just Runs

        repository = RecipeRepository(api, database, sessionStore)
    }

    @Test
    fun `create offline guarda receta ingredientes y pasos con syncVersion 0`() = runTest {
        coEvery { api.createRecipe(any(), any()) } throws IOException("sin red")
        val recipes = slot<List<RecipeEntity>>()
        val ingredients = slot<List<RecipeIngredientEntity>>()
        val steps = slot<List<RecipeStepEntity>>()
        coEvery { recipeDao.upsertAll(capture(recipes)) } just Runs
        coEvery { ingredientDao.upsertAll(capture(ingredients)) } just Runs
        coEvery { stepDao.upsertAll(capture(steps)) } just Runs

        repository.create(
            title = "Tortilla",
            description = "Clasica",
            servings = 4,
            prepMinutes = 10,
            cookMinutes = 20,
            difficulty = "EASY",
            ingredients = listOf(RecipeIngredientItemDto("Huevo", 4.0, "ud", null)),
            steps = listOf(RecipeStepItemDto("Batir y cuajar", 5))
        )

        val recipe = recipes.captured.single()
        assertEquals(FAMILY_ID, recipe.familyId)
        assertEquals("Tortilla", recipe.title)
        assertEquals(0L, recipe.syncVersion)
        assertFalse(recipe.deleted)
        assertTrue(recipe.id.isNotBlank())

        assertEquals(recipe.id, ingredients.captured.single().recipeId)
        assertEquals(1, ingredients.captured.single().position)
        assertEquals(0L, ingredients.captured.single().syncVersion)
        assertEquals(recipe.id, steps.captured.single().recipeId)
        assertEquals(1, steps.captured.single().position)
        assertEquals(0L, steps.captured.single().syncVersion)
    }

    @Test
    fun `update offline marca receta dirty conservando version base`() = runTest {
        coEvery { api.updateRecipe(any(), any(), any()) } throws IOException("sin red")
        val saved = slot<List<RecipeEntity>>()
        coEvery { recipeDao.upsertAll(capture(saved)) } just Runs

        repository.update(
            recipe = recipe("r1", syncVersion = 7L),
            title = "Nueva",
            description = null,
            servings = null,
            prepMinutes = null,
            cookMinutes = null,
            difficulty = null,
            ingredients = listOf(RecipeIngredientItemDto("No se envia offline", null, null, null)),
            steps = listOf(RecipeStepItemDto("No se envia offline", null))
        )

        assertEquals("Nueva", saved.captured.single().title)
        assertEquals(-7L, saved.captured.single().syncVersion)
        coVerify(exactly = 0) { api.replaceIngredients(any(), any(), any()) }
        coVerify(exactly = 0) { api.replaceSteps(any(), any(), any()) }
    }

    @Test
    fun `delete de receta creada offline elimina grafo local sin llamar API`() = runTest {
        repository.delete(recipe("r-new", syncVersion = 0L))

        coVerify(exactly = 1) { ingredientDao.deleteByRecipeId("r-new") }
        coVerify(exactly = 1) { stepDao.deleteByRecipeId("r-new") }
        coVerify(exactly = 1) { recipeDao.deleteById("r-new") }
        coVerify(exactly = 0) { api.deleteRecipe(any(), any()) }
    }

    @Test
    fun `delete offline de receta sincronizada marca tombstone dirty`() = runTest {
        coEvery { api.deleteRecipe(any(), any()) } throws IOException("sin red")
        val saved = slot<List<RecipeEntity>>()
        coEvery { recipeDao.upsertAll(capture(saved)) } just Runs

        repository.delete(recipe("r1", syncVersion = 3L))

        assertTrue(saved.captured.single().deleted)
        assertEquals(-3L, saved.captured.single().syncVersion)
    }

    @Test
    fun `create propaga CancellationException sin fallback local`() = runTest {
        coEvery { api.createRecipe(any(), any()) } throws CancellationException("cancelado")

        try {
            repository.create("Tortilla", null, null, null, null, null, emptyList(), emptyList())
            fail("Debe propagar CancellationException")
        } catch (_: CancellationException) {
            // esperado
        }
        coVerify(exactly = 0) { recipeDao.upsertAll(any()) }
    }

    private companion object {
        const val FAMILY_ID = "fam-1"

        fun recipe(id: String, syncVersion: Long) = RecipeEntity(
            id = id,
            familyId = FAMILY_ID,
            title = "Original",
            description = null,
            servings = null,
            prepMinutes = null,
            cookMinutes = null,
            difficulty = null,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion,
            deleted = false
        )
    }
}
