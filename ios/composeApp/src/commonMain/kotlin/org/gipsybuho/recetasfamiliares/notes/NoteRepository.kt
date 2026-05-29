package org.gipsybuho.recetasfamiliares.notes

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.CreateNoteRequest
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

    suspend fun createNote(title: String, body: String, pinned: Boolean): FamilyNoteDto {
        val familyId = session.familyId ?: error("Sin sesión activa")
        return apiClient.http.post("api/v1/families/$familyId/notes") {
            setBody(CreateNoteRequest(title, body, pinned))
        }.body()
    }

    suspend fun updateNote(id: String, title: String, body: String, pinned: Boolean): FamilyNoteDto {
        val familyId = session.familyId ?: error("Sin sesión activa")
        return apiClient.http.put("api/v1/families/$familyId/notes/$id") {
            setBody(CreateNoteRequest(title, body, pinned))
        }.body()
    }

    suspend fun deleteNote(id: String) {
        val familyId = session.familyId ?: error("Sin sesión activa")
        apiClient.http.delete("api/v1/families/$familyId/notes/$id")
    }
}
