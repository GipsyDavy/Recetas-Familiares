package org.gipsybuho.recetasfamiliares.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.gipsybuho.recetasfamiliares.core.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiClientTest {

    @Test
    fun `http client uses the injected mock engine instead of the platform engine`() = runTest {
        var requestedPath: String? = null
        val engine = MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val session = SessionStore().apply { accessToken = "token" }
        val client = ApiClient(session, engine = engine)

        client.http.get("api/v1/ping")

        assertEquals("/api/v1/ping", requestedPath)
    }
}
