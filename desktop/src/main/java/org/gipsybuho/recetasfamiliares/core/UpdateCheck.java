package org.gipsybuho.recetasfamiliares.core;

import java.net.URI;

import org.gipsybuho.recetasfamiliares.api.dto.AppVersionDtos.PlatformRelease;

/** Decide si hay que avisar de una version nueva. Logica pura, sin interfaz ni red. */
public final class UpdateCheck {

    private UpdateCheck() {}

    /**
     * @param release          lo que dice el servidor, o null si no hay nada publicado
     * @param currentVersion   la version de esta compilacion
     * @param dismissedVersion la ultima version que el usuario pidio no volver a ver
     */
    public static boolean shouldNotify(
            PlatformRelease release, String currentVersion, String dismissedVersion) {
        if (release == null || !isHttps(release.downloadUrl())) {
            return false;
        }
        if (!AppVersion.isNewer(release.latestVersion(), currentVersion)) {
            return false;
        }
        return !release.latestVersion().equals(dismissedVersion);
    }

    /**
     * Solo se acepta https. Un backend comprometido no debe poder llevar al
     * usuario a un file:// local ni a un http:// interceptable. Se compara el
     * esquema ya parseado, no el prefijo del texto: "httpsfalso://" empieza por
     * "https" y no es https.
     */
    private static boolean isHttps(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            return "https".equalsIgnoreCase(URI.create(url.trim()).getScheme());
        } catch (IllegalArgumentException urlMalformada) {
            return false;
        }
    }
}
