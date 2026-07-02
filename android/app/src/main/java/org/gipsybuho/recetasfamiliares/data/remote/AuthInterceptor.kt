package org.gipsybuho.recetasfamiliares.data.remote

import org.gipsybuho.recetasfamiliares.core.SessionStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore,
    baseUrl: String
) : Interceptor {

    // El token solo se envia al host del API: este cliente tambien lo usa Coil
    // para imagenes y no debe filtrar el Bearer a otros hosts.
    private val apiHost = baseUrl.toHttpUrl().host

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.accessToken
        val request = if (token.isNullOrBlank() || chain.request().url.host != apiHost) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
