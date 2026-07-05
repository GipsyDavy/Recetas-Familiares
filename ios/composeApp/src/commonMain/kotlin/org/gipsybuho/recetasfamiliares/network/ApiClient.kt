package org.gipsybuho.recetasfamiliares.network

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
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

    private val apiUrl: Url = Url(baseUrl)
    private val apiHost: String = apiUrl.host
    private val apiProtocol = apiUrl.protocol
    private val apiPort = apiUrl.port

    val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
        // SEC-6: adjunta Bearer y ante 401 refresca el token y reintenta una vez
        install(Auth) {
            bearer {
                // Enviar el Bearer proactivamente solo al host del API: evita el
                // doble round-trip del challenge 401 y no filtra el token a otros hosts
                sendWithoutRequest { request ->
                    request.url.host == apiHost &&
                        request.url.protocol == apiProtocol &&
                        request.url.port == apiPort
                }
                loadTokens {
                    session.accessToken?.let { access ->
                        BearerTokens(access, session.refreshToken)
                    }
                }
                refreshTokens {
                    // No refrescar ni reintentar con credenciales ante 401 de hosts
                    // ajenos al API (p.ej. una imagen externa cargada por Coil)
                    val requestUrl = response.call.request.url
                    if (requestUrl.host != apiHost ||
                        requestUrl.protocol != apiProtocol ||
                        requestUrl.port != apiPort
                    ) {
                        return@refreshTokens null
                    }
                    val refresh = session.refreshToken
                    if (refresh.isNullOrBlank()) {
                        session.clear()
                        return@refreshTokens null
                    }
                    val auth = runCatching {
                        client.post("api/v1/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequestDto(refresh))
                        }.body<AuthResponseDto>()
                    }.getOrNull()
                    if (auth == null) {
                        session.clear()
                        null
                    } else {
                        session.accessToken = auth.accessToken
                        session.refreshToken = auth.refreshToken
                        BearerTokens(auth.accessToken, auth.refreshToken)
                    }
                }
            }
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Limpia los tokens cacheados por el plugin Auth. Debe llamarse tras
     * login/logout para que la siguiente peticion relea SessionStore.
     */
    fun resetAuthTokens() {
        http.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
