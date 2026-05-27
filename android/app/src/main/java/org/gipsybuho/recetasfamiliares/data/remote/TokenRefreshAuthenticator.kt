package org.gipsybuho.recetasfamiliares.data.remote

import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.gipsybuho.recetasfamiliares.core.SessionStore
import org.gipsybuho.recetasfamiliares.data.remote.dto.AuthResponseDto

class TokenRefreshAuthenticator(
    private val sessionStore: SessionStore,
    private val baseUrl: String
) : Authenticator {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            sessionStore.clear()
            return null
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
                    .url("${baseUrl}api/v1/auth/refresh")
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

    private fun responseCount(response: Response): Int {
        var count = 1
        var r = response.priorResponse
        while (r != null) { count++; r = r.priorResponse }
        return count
    }
}
