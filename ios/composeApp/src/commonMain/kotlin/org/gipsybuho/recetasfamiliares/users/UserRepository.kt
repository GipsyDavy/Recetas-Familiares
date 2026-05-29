package org.gipsybuho.recetasfamiliares.users

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.UserResponseDto

class UserRepository(
    private val apiClient: ApiClient,
    private val session: SessionStore
) {
    suspend fun uploadAvatar(bytes: ByteArray): UserResponseDto {
        val response: UserResponseDto = apiClient.http.submitFormWithBinaryData(
            url = "api/v1/users/me/avatar",
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                })
            }
        ) {
            // Authorization header is added via defaultRequest in ApiClient
        }.body()
        session.avatarUrl = response.avatarUrl
        return response
    }

    suspend fun updateDisplayName(newName: String): UserResponseDto {
        val response: UserResponseDto = apiClient.http.put("api/v1/users/me") {
            setBody(mapOf("displayName" to newName.trim()))
        }.body()
        session.displayName = response.displayName
        return response
    }
}
