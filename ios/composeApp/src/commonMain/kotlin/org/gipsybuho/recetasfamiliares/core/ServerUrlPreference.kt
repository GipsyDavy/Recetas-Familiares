package org.gipsybuho.recetasfamiliares.core

import io.ktor.http.Url

object ServerUrlConfig {
    const val DEFAULT_API_BASE_URL = "https://recetas.167.233.213.242.sslip.io/"

    private val httpDevHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    fun normalizeAndValidate(rawBaseUrl: String?): String {
        val raw = rawBaseUrl ?: throw IllegalArgumentException("Introduce la URL del servidor.")
        if (raw.isBlank()) {
            throw IllegalArgumentException("Introduce la URL del servidor.")
        }
        if (raw.any { it.isWhitespace() }) {
            throw IllegalArgumentException("La URL del servidor no puede contener espacios.")
        }

        val authority = raw.substringAfter("://", missingDelimiterValue = "")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
        if (authority.contains("@")) {
            throw IllegalArgumentException("La URL del servidor no puede incluir usuario o contrasena.")
        }

        val url = runCatching { Url(raw) }.getOrElse {
            throw IllegalArgumentException("La URL del servidor no es valida.")
        }
        val scheme = url.protocol.name.lowercase()
        if (scheme != "https" && scheme != "http") {
            throw IllegalArgumentException("La URL del servidor debe usar https.")
        }
        val host = url.host.lowercase()
        if (host.isBlank()) {
            throw IllegalArgumentException("La URL del servidor debe incluir host.")
        }
        if (scheme == "http" && host !in httpDevHosts) {
            throw IllegalArgumentException("http solo esta permitido para hosts de desarrollo.")
        }
        if (url.encodedQuery.isNotEmpty() || url.fragment.isNotEmpty()) {
            throw IllegalArgumentException("La URL del servidor no puede incluir query ni fragmento.")
        }
        if (url.encodedPath.isNotBlank() && url.encodedPath != "/") {
            throw IllegalArgumentException("La URL del servidor debe apuntar al origen, sin ruta adicional.")
        }

        val explicitPort = authority.substringAfterLast(":", missingDelimiterValue = "")
            .toIntOrNull()
        val port = explicitPort?.let { ":$it" } ?: ""
        return "$scheme://$host$port/"
    }
}

expect class ServerUrlPreference() {
    var baseUrl: String
    fun reset()
}
