package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthFamilyDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthResponseDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthUserDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Privacidad en dispositivos compartidos: si inicia sesion un usuario
 * distinto al ultimo conocido, la cache local (Room) debe vaciarse antes
 * de cargar la sesion nueva.
 */
class AuthRepositoryUserChangeTest {

    private val api = mockk<RecetasApi>()
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private var wipes = 0

    private val repository = AuthRepository(api, sessionStore) { wipes++ }

    private fun stubLogin(userId: String) {
        coEvery { api.login(any()) } returns AuthResponseDto(
            accessToken = "access",
            refreshToken = "refresh",
            user = AuthUserDto(id = userId, email = "u@example.com", displayName = "U"),
            family = AuthFamilyDto(id = "fam-1", name = "Familia")
        )
        coEvery { api.families() } returns emptyList()
    }

    @Test
    fun `login de usuario distinto vacia la cache local`() = runTest {
        every { sessionStore.lastKnownUserId } returns "user-anterior"
        stubLogin("user-nuevo")

        repository.login("u@example.com", "password")

        assertEquals(1, wipes)
    }

    @Test
    fun `login del mismo usuario conserva la cache local`() = runTest {
        every { sessionStore.lastKnownUserId } returns "user-igual"
        stubLogin("user-igual")

        repository.login("u@example.com", "password")

        assertEquals(0, wipes)
    }

    @Test
    fun `primer login sin usuario previo no vacia nada`() = runTest {
        every { sessionStore.lastKnownUserId } returns null
        stubLogin("user-nuevo")

        repository.login("u@example.com", "password")

        assertEquals(0, wipes)
    }
}
