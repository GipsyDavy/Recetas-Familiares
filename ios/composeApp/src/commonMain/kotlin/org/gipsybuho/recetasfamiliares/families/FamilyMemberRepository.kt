package org.gipsybuho.recetasfamiliares.families

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.CreateFamilyRequestDto
import org.gipsybuho.recetasfamiliares.network.FamilyDto
import org.gipsybuho.recetasfamiliares.network.FamilyMemberResponseDto
import org.gipsybuho.recetasfamiliares.network.InviteMemberRequestDto
import org.gipsybuho.recetasfamiliares.network.UserRecipeRankingDto

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

    suspend fun recipeRanking(): List<UserRecipeRankingDto> {
        val familyId = session.familyId ?: return emptyList()
        return apiClient.http.get("api/v1/families/$familyId/recipe-rankings/users").body()
    }

    suspend fun families(): List<FamilyDto> =
        apiClient.http.get("api/v1/families").body()

    suspend fun createFamily(name: String): FamilyDto =
        apiClient.http.post("api/v1/families") {
            setBody(CreateFamilyRequestDto(name))
        }.body()

    suspend fun copyTargetFamilies(): List<FamilyDto> =
        copyTargets(families(), session.familyId)

    fun setActiveFamily(family: FamilyDto) {
        session.familyId = family.id
        session.familyRole = family.role
    }
}
