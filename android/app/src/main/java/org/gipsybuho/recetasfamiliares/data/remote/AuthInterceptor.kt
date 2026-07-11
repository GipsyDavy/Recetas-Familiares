package org.gipsybuho.recetasfamiliares.data.remote

import org.gipsybuho.recetasfamiliares.core.SessionStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val baseUrlProvider: () -> String
) : Interceptor {

    // El token solo se envia al origen del API: este cliente tambien lo usa Coil
    // para imagenes y no debe filtrar el Bearer a otros hosts.
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.accessToken
        val request = if (token.isNullOrBlank() || !isApiOrigin(chain.request().url)) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }

    private fun isApiOrigin(url: okhttp3.HttpUrl): Boolean {
        val api = baseUrlProvider().toHttpUrl()
        return url.scheme == api.scheme && url.host == api.host && url.port == api.port
    }
}
