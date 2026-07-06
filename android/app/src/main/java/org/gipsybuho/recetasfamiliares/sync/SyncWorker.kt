package org.gipsybuho.recetasfamiliares.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.gipsybuho.recetasfamiliares.RecetasApplication

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as RecetasApplication).container
        return SyncWorkerRunner.run {
            container.syncRepository.pushThenPull()
        }
    }
}

internal object SyncWorkerRunner {
    suspend fun run(pushThenPull: suspend () -> Unit): Result {
        return try {
            pushThenPull()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
