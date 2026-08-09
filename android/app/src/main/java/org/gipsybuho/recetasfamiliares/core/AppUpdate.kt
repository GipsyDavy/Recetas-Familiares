package org.gipsybuho.recetasfamiliares.core

import java.net.URI
import org.gipsybuho.recetasfamiliares.data.remote.dto.PlatformReleaseDto

/**
 * Decide si hay que avisar de una version nueva. Logica pura, sin red ni interfaz.
 * Espejo de AppVersion + UpdateCheck en Desktop.
 */
object AppUpdate {

    fun shouldNotify(release: PlatformReleaseDto?, currentVersion: String?): Boolean {
        val url = release?.downloadUrl ?: return false
        if (!isHttps(url)) return false
        return isNewer(release.latestVersion, currentVersion)
    }

    /** true si candidate es estrictamente mayor que current. Ante cualquier duda, false. */
    fun isNewer(candidate: String?, current: String?): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    /**
     * Solo https. Se compara el esquema ya parseado y no el prefijo del texto:
     * "httpsfalso://" empieza por "https" y no es https.
     */
    private fun isHttps(url: String): Boolean =
        runCatching { URI.create(url.trim()).scheme?.lowercase() == "https" }.getOrDefault(false)

    /** null si la cadena no es una version de numeros separados por puntos. */
    private fun parse(version: String?): List<Int>? {
        if (version.isNullOrBlank()) return null
        return version.trim().split(".").map { part ->
            part.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }
    }
}
