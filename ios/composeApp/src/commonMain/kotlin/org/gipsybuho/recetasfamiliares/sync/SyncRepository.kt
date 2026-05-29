package org.gipsybuho.recetasfamiliares.sync

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.AppDatabase
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.SyncPullResponseDto

class SyncRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore,
    driverFactory: DatabaseDriverFactory
) {
    private val db  = AppDatabase(driverFactory.createDriver())
    private val KEY = "last_sync_timestamp"

    suspend fun pullIncremental() {
        val familyId = session.familyId ?: return
        val since = db.syncMetadataQueries.getMetadata(KEY).executeAsOneOrNull()

        val response: SyncPullResponseDto = try {
            apiClient.http.get("api/v1/families/$familyId/sync/pull") {
                since?.let { parameter("since", it) }
            }.body()
        } catch (e: Exception) {
            return  // sin red — silencioso, los repos usarán el cache existente
        }

        response.recipes.forEach { dto ->
            db.recipesQueries.insertOrReplaceRecipe(
                id          = dto.id,
                familyId    = dto.familyId,
                title       = dto.title,
                description = dto.description,
                difficulty  = dto.difficulty,
                servings    = dto.servings?.toLong(),
                prepMinutes = dto.prepMinutes?.toLong(),
                cookMinutes = dto.cookMinutes?.toLong(),
                updatedAt   = dto.updatedAt,
                syncVersion = dto.syncVersion,
                deleted     = if (dto.deleted) 1L else 0L
            )
        }

        response.stockItems.forEach { dto ->
            db.stockItemsQueries.insertOrReplaceStockItem(
                id                = dto.id,
                familyId          = dto.familyId,
                name              = dto.name,
                quantity          = dto.quantity,
                unit              = dto.unit,
                expiresAt         = dto.expiresAt,
                lowStockThreshold = dto.lowStockThreshold,
                note              = dto.note,
                updatedAt         = dto.updatedAt,
                syncVersion       = dto.syncVersion,
                deleted           = if (dto.deleted) 1L else 0L
            )
        }

        db.syncMetadataQueries.setMetadata(KEY, response.serverTime)
    }
}
