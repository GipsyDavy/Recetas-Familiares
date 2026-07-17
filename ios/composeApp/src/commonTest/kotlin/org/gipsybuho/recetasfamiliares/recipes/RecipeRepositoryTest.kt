package org.gipsybuho.recetasfamiliares.recipes

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.database.DatabaseDriverFactory
import org.gipsybuho.recetasfamiliares.network.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeRepositoryTest {

    private fun sessionWithFamily(familyId: String?) = SessionStore().apply {
        accessToken = "token"
        this.familyId = familyId
    }

    private fun repository(engine: MockEngine, session: SessionStore) =
        RecipeRepository(ApiClient(session, engine = engine), session, DatabaseDriverFactory())

    @Test
    fun `copyToFamily posts target family and returns true on 201`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/families/f1/recipes/r1/copy", request.url.encodedPath)
            body = request.body.toByteArray().decodeToString()
            respond(
                content = """{"id":"r2","familyId":"f2","title":"Tortilla","createdAt":"2026-07-17T00:00:00Z","updatedAt":"2026-07-17T00:00:00Z","syncVersion":1,"deleted":false}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val session = sessionWithFamily("f1")

        val result = repository(engine, session).copyToFamily("r1", "f2")

        assertTrue(result)
        assertTrue(body.contains(""""targetFamilyId":"f2""""))
    }

    @Test
    fun `copyToFamily returns false on 403`() = runTest {
        val engine = MockEngine { respond("denied", HttpStatusCode.Forbidden) }
        val session = sessionWithFamily("f1")

        assertFalse(repository(engine, session).copyToFamily("r1", "f2"))
    }

    @Test
    fun `copyToFamily returns false without family session and makes no request`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("", HttpStatusCode.OK) }
        val session = sessionWithFamily(null)

        assertFalse(repository(engine, session).copyToFamily("r1", "f2"))
        assertEquals(0, calls)
    }
}
