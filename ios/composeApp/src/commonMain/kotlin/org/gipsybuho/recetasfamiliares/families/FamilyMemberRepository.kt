package org.gipsybuho.recetasfamiliares.families

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.FamilyMemberResponseDto
import org.gipsybuho.recetasfamiliares.network.InviteMemberRequestDto

class FamilyMemberRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun invite(email: String, role: String): FamilyMemberResponseDto {
        val familyId = session.familyId ?: error("No family session")
        return apiClient.http.post("api/v1/families/$familyId/members") {
            setBody(InviteMemberRequestDto(email.trim(), role))
        }.body()
    }
}
