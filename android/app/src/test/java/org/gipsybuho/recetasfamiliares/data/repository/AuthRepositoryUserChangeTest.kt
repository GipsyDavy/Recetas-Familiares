package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthFamilyDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthResponseDto
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthUserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `el wipe corre antes de escribir la sesion nueva`() = runTest {
        val events = mutableListOf<String>()
        every { sessionStore.lastKnownUserId } returns "user-anterior"
        every { sessionStore.accessToken = any() } answers { events += "session" }
        stubLogin("user-nuevo")
        val repo = AuthRepository(api, sessionStore) { events += "wipe" }

        repo.login("u@example.com", "password")

        assertEquals(listOf("wipe", "session"), events)
    }

    @Test
    fun `si el wipe falla el login falla sin escribir la sesion`() = runTest {
        every { sessionStore.lastKnownUserId } returns "user-anterior"
        stubLogin("user-nuevo")
        val repo = AuthRepository(api, sessionStore) { error("wipe fallido") }

        val result = runCatching { repo.login("u@example.com", "password") }

        assertTrue(result.isFailure)
        verify(exactly = 0) { sessionStore.accessToken = any() }
    }

    @Test
    fun `pendingWipe fuerza el vaciado aunque el usuario coincida`() = runTest {
        every { sessionStore.lastKnownUserId } returns "user-igual"
        every { sessionStore.pendingWipe } returns true
        stubLogin("user-igual")

        repository.login("u@example.com", "password")

        assertEquals(1, wipes)
        verify { sessionStore.pendingWipe = false }
    }
}
