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
import org.gipsybuho.recetasfamiliares.core.ServerUrlPreference

class ApiClient(
    private val session: SessionStore,
    private val serverUrlPreference: ServerUrlPreference = ServerUrlPreference()
) {

    val baseUrl: String
        get() = serverUrlPreference.baseUrl

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
                    isApiOrigin(request.url)
                }
                loadTokens {
                    session.accessToken?.let { access ->
                        BearerTokens(access, session.refreshToken)
                    }
                }
                refreshTokens {
                    // No refrescar ni reintentar con credenciales ante 401 de hosts
                    // ajenos al API (p.ej. una imagen externa cargada por Coil)
                    if (!isApiOrigin(response.call.request.url)) {
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

    private fun isApiOrigin(requestUrl: Url): Boolean {
        val apiUrl = Url(baseUrl)
        return requestUrl.host == apiUrl.host &&
            requestUrl.protocol == apiUrl.protocol &&
            requestUrl.port == apiUrl.port
    }

    private fun isApiOrigin(requestUrl: URLBuilder): Boolean = isApiOrigin(requestUrl.build())
}
