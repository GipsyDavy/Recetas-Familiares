package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.core.content.edit
import org.gipsybuho.recetasfamiliares.BuildConfig
import java.net.URI

object ServerUrlConfig {
    val DEFAULT_API_BASE_URL: String = BuildConfig.DEFAULT_API_BASE_URL

    private val httpDevHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    fun normalizeAndValidate(rawBaseUrl: String?): String {
        val raw = rawBaseUrl ?: throw IllegalArgumentException("Introduce la URL del servidor.")
        if (raw.isBlank()) {
            throw IllegalArgumentException("Introduce la URL del servidor.")
        }
        if (raw.any { it.isWhitespace() }) {
            throw IllegalArgumentException("La URL del servidor no puede contener espacios.")
        }

        val uri = runCatching { URI(raw) }.getOrElse {
            throw IllegalArgumentException("La URL del servidor no es valida.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            throw IllegalArgumentException("La URL del servidor debe usar https.")
        }
        if (uri.rawUserInfo != null) {
            throw IllegalArgumentException("La URL del servidor no puede incluir usuario o contrasena.")
        }
        val host = uri.host?.lowercase()
            ?: throw IllegalArgumentException("La URL del servidor debe incluir host.")
        if (scheme == "http" && host !in httpDevHosts) {
            throw IllegalArgumentException("http solo esta permitido para hosts de desarrollo.")
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            throw IllegalArgumentException("La URL del servidor no puede incluir query ni fragmento.")
        }
        val path = uri.rawPath
        if (!path.isNullOrBlank() && path != "/") {
            throw IllegalArgumentException("La URL del servidor debe apuntar al origen, sin ruta adicional.")
        }

        return URI(scheme, null, host, uri.port, "/", null, null).toString()
    }
}

class ServerUrlStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseUrl: String
        get() {
            val saved = preferences.getString(KEY_BASE_URL, null)
            if (!saved.isNullOrBlank()) {
                return runCatching { ServerUrlConfig.normalizeAndValidate(saved) }
                    .getOrElse {
                        preferences.edit { remove(KEY_BASE_URL) }
                        ServerUrlConfig.DEFAULT_API_BASE_URL
                    }
            }
            return ServerUrlConfig.DEFAULT_API_BASE_URL
        }
        set(value) {
            preferences.edit { putString(KEY_BASE_URL, ServerUrlConfig.normalizeAndValidate(value)) }
        }

    fun reset() {
        preferences.edit { remove(KEY_BASE_URL) }
    }

    private companion object {
        const val PREFS_NAME = "recetas_server_config"
        const val KEY_BASE_URL = "api_base_url"
    }
}
