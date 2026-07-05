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
        var since = db.syncMetadataQueries.getMetadata(KEY).executeAsOneOrNull()
        var pages = 0
        while (true) {
            val response: SyncPullResponseDto = try {
                apiClient.http.get("api/v1/families/$familyId/sync/pull") {
                    since?.let { parameter("since", it) }
                    parameter("limit", PULL_PAGE_SIZE)
                }.body()
            } catch (e: Exception) {
                return  // sin red — silencioso, los repos usarán el cache existente
            }
            applyPage(response)
            if (!response.hasMore || response.nextSince == null || ++pages >= MAX_PULL_PAGES) {
                // el cursor persistido solo avanza al completar el pull; si se
                // interrumpe, la próxima sincronización repite páginas (upsert idempotente)
                db.syncMetadataQueries.setMetadata(KEY, response.serverTime)
                return
            }
            since = response.nextSince
        }
    }

    private fun applyPage(response: SyncPullResponseDto) {
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

        response.ingredients.forEach { dto ->
            db.recipeIngredientsQueries.insertOrReplaceIngredient(
                id       = dto.id,
                recipeId = dto.recipeId,
                name     = dto.name,
                deleted  = if (dto.deleted) 1L else 0L
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
    }

    private companion object {
        const val PULL_PAGE_SIZE = 200

        /** Corta el bucle ante un servidor que nunca deja de responder hasMore. */
        const val MAX_PULL_PAGES = 50
    }
}
