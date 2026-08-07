package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.UserResponseDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El perfil propio del servidor tiene que quedar guardado en la sesion: el login
 * no trae el avatar (AuthUserResponse no lo incluye) y sin persistirlo la app
 * vuelve a las iniciales en cada arranque.
 */
class UserRepositoryTest {

    private val api = mockk<RecetasApi>()
    private val sessionStore = mockk<SessionStore>(relaxed = true)

    @Test
    fun `me guarda en la sesion el avatar que devuelve el servidor`() = runTest {
        var savedAvatar: String? = NOT_TOUCHED
        every { sessionStore.avatarUrl = any() } answers { savedAvatar = firstArg() }
        coEvery { api.getMe() } returns userResponse(AVATAR_URL)

        UserRepository(api, sessionStore).me()

        assertEquals(AVATAR_URL, savedAvatar)
    }

    @Test
    fun `me borra el avatar de la sesion si la cuenta ya no tiene`() = runTest {
        var savedAvatar: String? = NOT_TOUCHED
        every { sessionStore.avatarUrl = any() } answers { savedAvatar = firstArg() }
        coEvery { api.getMe() } returns userResponse(null)

        UserRepository(api, sessionStore).me()

        assertEquals(
            "Un avatar borrado en otro dispositivo no puede seguir viendose aqui",
            null,
            savedAvatar
        )
    }

    private fun userResponse(avatarUrl: String?) = UserResponseDto(
        id = "u1",
        email = "emma@example.test",
        displayName = "Emma",
        avatarUrl = avatarUrl,
        emailVerified = true
    )

    private companion object {
        const val AVATAR_URL = "https://api.example.test/uploads/avatar-u1.jpg"
        const val NOT_TOUCHED = "no-se-escribio-nada"
    }
}
