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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.local.StockDao
import org.gipsybuho.recetasfamiliares.data.local.StockItemEntity
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Convencion offline (COD-3):
 *  - syncVersion > 0  -> sincronizada (version del servidor).
 *  - syncVersion = 0  -> creada offline, aun sin servidor.
 *  - syncVersion < 0  -> editada/borrada offline; el absoluto conserva la version base.
 */
class StockRepositoryOfflineTest {

    private val api = mockk<RecetasApi>()
    private val stockDao = mockk<StockDao>()
    private val sessionStore = mockk<SessionStore>()

    private lateinit var repository: StockRepository

    @Before
    fun setUp() {
        every { sessionStore.familyId } returns FAMILY_ID
        every { sessionStore.familyIdFlow } returns MutableStateFlow(FAMILY_ID)
        every { stockDao.observeStock(FAMILY_ID) } returns emptyFlow()
        coEvery { stockDao.upsertAll(any()) } just Runs
        coEvery { stockDao.deleteById(any()) } just Runs
        repository = StockRepository(api, stockDao, sessionStore)
    }

    @Test
    fun `create offline guarda localmente con syncVersion 0`() = runTest {
        coEvery { api.createStockItem(any(), any()) } throws IOException("sin red")
        val saved = slot<List<StockItemEntity>>()
        coEvery { stockDao.upsertAll(capture(saved)) } just Runs

        repository.create("Harina", 1.0, "kg", null, null, null)

        val item = saved.captured.single()
        assertEquals(0L, item.syncVersion)
        assertEquals("Harina", item.name)
        assertEquals(false, item.deleted)
    }

    @Test
    fun `update offline marca dirty conservando la version base del servidor`() = runTest {
        coEvery { api.updateStockItem(any(), any(), any()) } throws IOException("sin red")
        val saved = slot<List<StockItemEntity>>()
        coEvery { stockDao.upsertAll(capture(saved)) } just Runs

        repository.update(stockItem("s1", syncVersion = 5L), "Sal", 2.0, "kg", null, null, null)

        assertEquals(-5L, saved.captured.single().syncVersion)
        assertEquals("Sal", saved.captured.single().name)
    }

    @Test
    fun `update offline repetido mantiene la version dirty existente`() = runTest {
        coEvery { api.updateStockItem(any(), any(), any()) } throws IOException("sin red")
        val saved = slot<List<StockItemEntity>>()
        coEvery { stockDao.upsertAll(capture(saved)) } just Runs

        repository.update(stockItem("s1", syncVersion = -5L), "Sal", 2.0, "kg", null, null, null)

        assertEquals(-5L, saved.captured.single().syncVersion)
    }

    @Test
    fun `delete de item creado offline borra localmente sin llamar a la API`() = runTest {
        repository.delete(stockItem("s1", syncVersion = 0L))

        coVerify(exactly = 1) { stockDao.deleteById("s1") }
        coVerify(exactly = 0) { api.deleteStockItem(any(), any()) }
    }

    @Test
    fun `delete offline marca deleted con version dirty`() = runTest {
        coEvery { api.deleteStockItem(any(), any()) } throws IOException("sin red")
        val saved = slot<List<StockItemEntity>>()
        coEvery { stockDao.upsertAll(capture(saved)) } just Runs

        repository.delete(stockItem("s1", syncVersion = 4L))

        val item = saved.captured.single()
        assertTrue(item.deleted)
        assertEquals(-4L, item.syncVersion)
    }

    @Test
    fun `CancellationException se propaga sin fallback offline`() = runTest {
        coEvery { api.createStockItem(any(), any()) } throws CancellationException("cancelado")

        try {
            repository.create("Harina", 1.0, "kg", null, null, null)
            fail("Debe propagar CancellationException")
        } catch (_: CancellationException) {
            // esperado
        }
        coVerify(exactly = 0) { stockDao.upsertAll(any()) }
    }

    private companion object {
        const val FAMILY_ID = "fam-1"

        fun stockItem(id: String, syncVersion: Long) = StockItemEntity(
            id = id, familyId = FAMILY_ID, name = "Harina", quantity = 1.0, unit = "kg",
            lowStockThreshold = null, expiresAt = null, note = null,
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            syncVersion = syncVersion, deleted = false
        )
    }
}
