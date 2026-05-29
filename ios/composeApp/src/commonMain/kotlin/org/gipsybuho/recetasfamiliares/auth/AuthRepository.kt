package org.gipsybuho.recetasfamiliares.auth

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.AuthResponseDto
import org.gipsybuho.recetasfamiliares.network.LoginRequestDto

class AuthRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun login(email: String, password: String) {
        val response: AuthResponseDto = apiClient.http
            .post("api/v1/auth/login") { setBody(LoginRequestDto(email.trim(), password)) }
            .body()
        session.accessToken  = response.accessToken
        session.refreshToken = response.refreshToken
        session.familyId     = response.family.id
        session.userId       = response.user.id
        session.displayName  = response.user.displayName
        session.email        = response.user.email
    }

    fun logout() = session.clear()
}
