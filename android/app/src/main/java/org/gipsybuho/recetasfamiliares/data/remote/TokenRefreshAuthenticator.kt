package org.gipsybuho.recetasfamiliares.data.remote

import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthResponseDto
import java.util.concurrent.TimeUnit

class TokenRefreshAuthenticator(
    private val sessionStore: SessionStore,
    private val baseUrlProvider: () -> String
) : Authenticator {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val refreshLock = Any()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Nunca responder con credenciales a un 401 de un host ajeno al API
        if (!isApiOrigin(response.request.url)) {
            return null
        }
        if (responseCount(response) >= 2) {
            sessionStore.clear()
            return null
        }
        // Single-flight: el backend rota el refresh token de forma atomica
        // (revokeIfActive); dos refresh concurrentes con el mismo token
        // revocarian la sesion entera. Solo un hilo refresca; el resto
        // reintenta con el token que dejo el primero.
        val failedAccessToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
        synchronized(refreshLock) {
            val current = sessionStore.accessToken
            if (!current.isNullOrBlank() && current != failedAccessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .build()
            }
            val refreshToken = sessionStore.refreshToken ?: run {
                sessionStore.clear()
                return null
            }

            val body = gson.toJson(mapOf("refreshToken" to refreshToken))
                .toRequestBody(jsonType)

            val refreshResponse = runCatching {
                client.newCall(
                    Request.Builder()
                        .url("${baseUrlProvider()}api/v1/auth/refresh")
                        .post(body)
                        .build()
                ).execute()
            }.getOrNull() ?: run {
                sessionStore.clear()
                return null
            }

            if (!refreshResponse.isSuccessful) {
                sessionStore.clear()
                return null
            }

            val auth = runCatching {
                gson.fromJson(refreshResponse.body?.string(), AuthResponseDto::class.java)
            }.getOrNull() ?: run {
                sessionStore.clear()
                return null
            }

            sessionStore.accessToken = auth.accessToken
            sessionStore.refreshToken = auth.refreshToken

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
        }
    }

    private fun isApiOrigin(url: okhttp3.HttpUrl): Boolean {
        val api = baseUrlProvider().toHttpUrl()
        return url.scheme == api.scheme && url.host == api.host && url.port == api.port
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var r = response.priorResponse
        while (r != null) { count++; r = r.priorResponse }
        return count
    }
}
