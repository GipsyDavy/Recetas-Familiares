package org.gipsybuho.recetasfamiliares.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.RecetasApi
import org.gipsybuho.recetasfamiliares.data.remote.dto.PresenceResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyMemberRepositoryPresenceTest {

    private val api = mockk<RecetasApi>()
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val repository = FamilyMemberRepository(api, sessionStore)

    @Test
    fun presenceReturnsOnlineUserIdsForActiveFamily() = runTest {
        every { sessionStore.familyId } returns "family-1"
        coEvery { api.presence("family-1") } returns PresenceResponseDto(onlineUserIds = listOf("user-a"))

        val result = repository.presence()

        assertEquals(listOf("user-a"), result.onlineUserIds)
    }

    @Test
    fun presenceReturnsEmptyWithoutActiveFamily() = runTest {
        every { sessionStore.familyId } returns null

        val result = repository.presence()

        assertTrue(result.onlineUserIds.isEmpty())
    }
}
