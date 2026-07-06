package org.gipsybuho.recetasfamiliares.sync

import androidx.work.ListenableWorker.Result
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SyncWorkerRunnerTest {

    @Test
    fun `run devuelve success cuando pushThenPull completa`() = runTest {
        var called = false

        val result = SyncWorkerRunner.run {
            called = true
        }

        assertEquals(Result.success(), result)
        assertEquals(true, called)
    }

    @Test
    fun `run devuelve retry ante fallo recuperable`() = runTest {
        val result = SyncWorkerRunner.run {
            throw IOException("sin red")
        }

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `run propaga CancellationException sin convertirla en retry`() = runTest {
        try {
            SyncWorkerRunner.run {
                throw CancellationException("cancelado")
            }
            fail("Debe propagar CancellationException")
        } catch (_: CancellationException) {
            // esperado
        }
    }
}
