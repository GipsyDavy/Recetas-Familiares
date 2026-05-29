package org.gipsybuho.recetasfamiliares.data.repository

import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.UpdateUserRequestDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.UserResponseDto

class UserRepository(
    private val api: RecetasApi,
    private val sessionStore: SessionStore
) {
    suspend fun updateDisplayName(newName: String): UserResponseDto {
        val response = api.updateMe(UpdateUserRequestDto(newName.trim()))
        sessionStore.displayName = response.displayName
        return response
    }
}
