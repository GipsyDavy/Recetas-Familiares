package org.gipsybuho.recetasfamiliares.notes

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.FamilyNoteDto
import org.gipsybuho.recetasfamiliares.network.PageDto

class NoteRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun loadNotes(page: Int = 0, size: Int = 30): List<FamilyNoteDto> {
        val familyId = session.familyId ?: return emptyList()
        val response: PageDto<FamilyNoteDto> = apiClient.http
            .get("api/v1/families/$familyId/notes") {
                parameter("page", page)
                parameter("size", size)
            }.body()
        return response.items.filter { !it.deleted }
    }
}
