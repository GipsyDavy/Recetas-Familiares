package org.gipsybuho.recetasfamiliares.api.dto;

/**
 * Respuesta de GET /api/v1/app-version. Cada plataforma llega nula mientras no
 * haya una version publicada configurada en el servidor.
 */
public final class AppVersionDtos {

    private AppVersionDtos() {}

    public record PlatformRelease(String latestVersion, String downloadUrl) {}

    public record AppVersionResponse(PlatformRelease desktop, PlatformRelease android) {}
}
