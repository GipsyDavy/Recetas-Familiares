package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteDao
import org.gipsybuho.recetasfamiliares.data.local.FamilyNoteEntity
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeDao
import org.gipsybuho.recetasfamiliares.data.local.FavoriteRecipeEntity
import org.gipsybuho.recetasfamiliares.data.local.RecetasDatabase
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoriteAndNoteRepositoryOfflineTest {

    private val api = mockk<RecetasApi>()
    private val database = mockk<RecetasDatabase>()
    private val sessionStore = mockk<SessionStore>()
    private val favoriteDao = mockk<FavoriteRecipeDao>()
    private val noteDao = mockk<FamilyNoteDao>()

    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var noteRepository: FamilyNoteRepository

    @Before
    fun setUp() {
        every { sessionStore.familyId } returns FAMILY_ID
        every { database.favoriteRecipeDao() } returns favoriteDao
        every { database.familyNoteDao() } returns noteDao
        every { favoriteDao.observeFavorites() } returns emptyFlow()
        every { noteDao.observeNotes() } returns emptyFlow()

        coEvery { favoriteDao.upsertAll(any()) } just Runs
        coEvery { favoriteDao.deleteById(any()) } just Runs
        coEvery { noteDao.upsertAll(any()) } just Runs
        coEvery { noteDao.deleteById(any()) } just Runs

        favoriteRepository = FavoriteRepository(api, database, sessionStore)
        noteRepository = FamilyNoteRepository(api, database, sessionStore)
    }

    @Test
    fun `toggle favorite offline crea favorito pendiente con syncVersion 0`() = runTest {
        coEvery { favoriteDao.findByRecipeId("r1") } returns null
        coEvery { api.addFavorite(any(), any()) } throws IOException("sin red")
        val saved = slot<List<FavoriteRecipeEntity>>()
        coEvery { favoriteDao.upsertAll(capture(saved)) } just Runs

        favoriteRepository.toggle("r1")

        val favorite = saved.captured.single()
        assertEquals(FAMILY_ID, favorite.familyId)
        assertEquals("r1", favorite.recipeId)
        assertEquals(0L, favorite.syncVersion)
        assertFalse(favorite.deleted)
    }

    @Test
    fun `toggle favorite creado offline elimina localmente sin llamar API`() = runTest {
        coEvery { favoriteDao.findByRecipeId("r1") } returns favorite("f1", "r1", syncVersion = 0L)

        favoriteRepository.toggle("r1")

        coVerify(exactly = 1) { favoriteDao.deleteById("f1") }
        coVerify(exactly = 0) { api.removeFavorite(any(), any()) }
        coVerify(exactly = 0) { favoriteDao.upsertAll(any()) }
    }

    @Test
    fun `toggle favorite sincronizado offline marca deleted con version dirty`() = runTest {
        coEvery { favoriteDao.findByRecipeId("r1") } returns favorite("f1", "r1", syncVersion = 4L)
        coEvery { api.removeFavorite(any(), any()) } throws IOException("sin red")
        val saved = slot<List<FavoriteRecipeEntity>>()
        coEvery { favoriteDao.upsertAll(capture(saved)) } just Runs

        favoriteRepository.toggle("r1")

        assertTrue(saved.captured.single().deleted)
        assertEquals(-4L, saved.captured.single().syncVersion)
    }

    @Test
    fun `create note offline guarda nota pendiente con syncVersion 0`() = runTest {
        coEvery { api.createNote(any(), any()) } throws IOException("sin red")
        val saved = slot<List<FamilyNoteEntity>>()
        coEvery { noteDao.upsertAll(capture(saved)) } just Runs

        noteRepository.create("Comprar", "Pan y leche", pinned = true)

        val note = saved.captured.single()
        assertEquals(FAMILY_ID, note.familyId)
        assertEquals("Comprar", note.title)
        assertEquals("Pan y leche", note.body)
        assertTrue(note.pinned)
        assertEquals(0L, note.syncVersion)
        assertFalse(note.deleted)
    }

    @Test
    fun `update note offline conserva version base en negativo`() = runTest {
        coEvery { api.updateNote(any(), any(), any()) } throws IOException("sin red")
        val saved = slot<List<FamilyNoteEntity>>()
        coEvery { noteDao.upsertAll(capture(saved)) } just Runs

        noteRepository.update(note("n1", syncVersion = 6L), "Nuevo", "Texto", pinned = true)

        assertEquals("Nuevo", saved.captured.single().title)
        assertEquals("Texto", saved.captured.single().body)
        assertTrue(saved.captured.single().pinned)
        assertEquals(-6L, saved.captured.single().syncVersion)
    }

    @Test
    fun `delete note creada offline elimina localmente sin llamar API`() = runTest {
        noteRepository.delete(note("n1", syncVersion = 0L))

        coVerify(exactly = 1) { noteDao.deleteById("n1") }
        coVerify(exactly = 0) { api.deleteNote(any(), any()) }
    }

    @Test
    fun `delete note sincronizada offline marca tombstone dirty`() = runTest {
        coEvery { api.deleteNote(any(), any()) } throws IOException("sin red")
        val saved = slot<List<FamilyNoteEntity>>()
        coEvery { noteDao.upsertAll(capture(saved)) } just Runs

        noteRepository.delete(note("n1", syncVersion = 8L))

        assertTrue(saved.captured.single().deleted)
        assertEquals(-8L, saved.captured.single().syncVersion)
    }

    private companion object {
        const val FAMILY_ID = "fam-1"

        fun favorite(id: String, recipeId: String, syncVersion: Long) = FavoriteRecipeEntity(
            id = id,
            familyId = FAMILY_ID,
            recipeId = recipeId,
            recipeTitle = null,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion,
            deleted = false
        )

        fun note(id: String, syncVersion: Long) = FamilyNoteEntity(
            id = id,
            familyId = FAMILY_ID,
            recipeId = null,
            recipeTitle = null,
            title = "Nota",
            body = "Texto",
            pinned = false,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion,
            deleted = false
        )
    }
}
