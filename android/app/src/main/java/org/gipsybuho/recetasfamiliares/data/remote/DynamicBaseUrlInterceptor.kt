package org.gipsybuho.recetasfamiliares.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor(
    initialBaseUrl: String,
    private val baseUrlProvider: () -> String
) : Interceptor {

    private val initialOrigin: HttpUrl = initialBaseUrl.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!sameOrigin(request.url, initialOrigin)) {
            return chain.proceed(request)
        }

        val currentOrigin = baseUrlProvider().toHttpUrl()
        val rewritten = request.url.newBuilder()
            .scheme(currentOrigin.scheme)
            .host(currentOrigin.host)
            .port(currentOrigin.port)
            .build()

        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private fun sameOrigin(left: HttpUrl, right: HttpUrl): Boolean =
        left.scheme == right.scheme && left.host == right.host && left.port == right.port
}
