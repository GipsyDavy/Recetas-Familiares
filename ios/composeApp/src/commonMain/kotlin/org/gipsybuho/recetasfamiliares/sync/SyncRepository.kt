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
    private val KEY_PREFIX = "last_sync_timestamp:"

    suspend fun pullIncremental() {
        val familyId = session.familyId ?: return
        val cursorKey = KEY_PREFIX + familyId
        var since = db.appDatabaseQueries.getMetadata(cursorKey).executeAsOneOrNull()?.value_
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
            applyPage(familyId, response)
            val hasNextPage = response.hasMore && response.nextSince != null
            if (!hasNextPage) {
                // el cursor persistido solo avanza al completar el pull; si se
                // interrumpe, la próxima sincronización repite páginas (upsert idempotente)
                db.appDatabaseQueries.setMetadata(cursorKey, response.serverTime)
                return
            }
            if (++pages >= MAX_PULL_PAGES) {
                // Corte defensivo sin avanzar cursor: evita saltar filas pendientes.
                return
            }
            since = response.nextSince
        }
    }

    /** Defensa en profundidad: descarta filas cuyo familyId no coincida con el pull solicitado. */
    private fun applyPage(familyId: String, response: SyncPullResponseDto) {
        response.recipes.filter { it.familyId == familyId }.forEach { dto ->
            db.appDatabaseQueries.insertOrReplaceRecipe(
                id          = dto.id,
                familyId    = dto.familyId,
                title       = dto.title,
                description = dto.description,
                difficulty  = dto.difficulty,
                servings    = dto.servings?.toLong(),
                prepMinutes = dto.prepMinutes?.toLong(),
                cookMinutes = dto.cookMinutes?.toLong(),
                createdByUserId = dto.createdByUserId,
                createdByDisplayName = dto.createdByDisplayName,
                updatedAt   = dto.updatedAt,
                syncVersion = dto.syncVersion,
                deleted     = if (dto.deleted) 1L else 0L
            )
        }

        response.ingredients.forEach { dto ->
            val recipeId = dto.recipeId ?: return@forEach
            db.appDatabaseQueries.insertOrReplaceIngredient(
                id       = dto.id,
                recipeId = recipeId,
                name     = dto.name,
                deleted  = if (dto.deleted) 1L else 0L
            )
        }

        response.stockItems.filter { it.familyId == familyId }.forEach { dto ->
            db.appDatabaseQueries.insertOrReplaceStockItem(
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
