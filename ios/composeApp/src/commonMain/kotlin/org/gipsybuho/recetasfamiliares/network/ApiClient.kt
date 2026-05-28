package org.gipsybuho.recetasfamiliares.network

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.gipsybuho.recetasfamiliares.core.SessionStore

class ApiClient(private val session: SessionStore) {

    // Para el emulador iOS en macOS con backend local usar: http://localhost:8080/
    // Para dispositivo físico en la misma red usar: http://<IP-del-Mac>:8080/
    val baseUrl: String = "http://localhost:8080/"

    val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            session.accessToken?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
}
