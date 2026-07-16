package org.gipsybuho.recetasfamiliares.families

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.network.ApiClient
import org.gipsybuho.recetasfamiliares.network.FamilyDto
import kotlin.test.Test
import kotlin.test.assertEquals

class FamilyMemberRepositoryTest {

    private fun sessionWithFamily(familyId: String?) = SessionStore().apply {
        accessToken = "token"
        this.familyId = familyId
    }

    @Test
    fun `families lists families from backend`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/families", request.url.encodedPath)
            respond(
                content = """[{"id":"f1","name":"Casa","role":"OWNER","avatarUrl":null},{"id":"f2","name":"Casa2","role":"MEMBER","avatarUrl":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = sessionWithFamily("f1")
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)

        val result = repository.families()

        assertEquals(2, result.size)
        assertEquals("f2", result[1].id)
    }

    @Test
    fun `createFamily posts name and returns created family`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/families", request.url.encodedPath)
            respond(
                content = """{"id":"f3","name":"Nueva","role":"OWNER","avatarUrl":null}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = sessionWithFamily("f1")
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)

        val created = repository.createFamily("Nueva")

        assertEquals("f3", created.id)
    }

    @Test
    fun `setActiveFamily persists id and role without any network call`() {
        var calls = 0
        val engine = MockEngine { calls++; respond("", HttpStatusCode.OK) }
        val session = sessionWithFamily("f1")
        val repository = FamilyMemberRepository(ApiClient(session, engine = engine), session)

        repository.setActiveFamily(FamilyDto("f2", "Casa2", "MEMBER", null))

        assertEquals("f2", session.familyId)
        assertEquals("MEMBER", session.familyRole)
        assertEquals(0, calls)
    }
}
