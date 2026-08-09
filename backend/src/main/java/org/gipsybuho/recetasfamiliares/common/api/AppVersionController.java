package org.gipsybuho.recetasfamiliares.common.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Version recomendada de cada aplicacion cliente y donde descargarla.
 *
 * Publico a proposito: el cliente lo consulta al arrancar, con la pantalla de
 * login todavia delante, y la respuesta no contiene ningun dato personal.
 *
 * Se configura por variables de entorno. Sin configurar, cada plataforma viaja
 * nula y el cliente no avisa de nada, que es el comportamiento correcto
 * mientras no haya ninguna version publicada.
 */
@RestController
@RequestMapping("/api/v1/app-version")
public class AppVersionController {

    private final PlatformRelease desktop;
    private final PlatformRelease android;

    public AppVersionController(
            @Value("${app.update.desktop.version:}") String desktopVersion,
            @Value("${app.update.desktop.url:}") String desktopUrl,
            @Value("${app.update.android.version:}") String androidVersion,
            @Value("${app.update.android.url:}") String androidUrl
    ) {
        this.desktop = release(desktopVersion, desktopUrl);
        this.android = release(androidVersion, androidUrl);
    }

    @GetMapping
    public AppVersionResponse appVersion() {
        return new AppVersionResponse(desktop, android);
    }

    /** Una version sin URL, o una URL sin version, es configuracion a medias: no se propone. */
    private static PlatformRelease release(String version, String url) {
        if (version == null || version.isBlank() || url == null || url.isBlank()) {
            return null;
        }
        return new PlatformRelease(version.trim(), url.trim());
    }

    public record PlatformRelease(String latestVersion, String downloadUrl) {}

    public record AppVersionResponse(PlatformRelease desktop, PlatformRelease android) {}
}
