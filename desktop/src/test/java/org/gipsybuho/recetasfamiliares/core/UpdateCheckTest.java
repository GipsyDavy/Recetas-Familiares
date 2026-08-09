package org.gipsybuho.recetasfamiliares.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gipsybuho.recetasfamiliares.api.dto.AppVersionDtos.PlatformRelease;
import org.junit.jupiter.api.Test;

class UpdateCheckTest {

    private static final String RELEASES =
            "https://github.com/GipsyDavy/Recetas-Familiares/releases/latest";

    @Test
    void avisaCuandoHayUnaVersionMasNueva() {
        assertTrue(UpdateCheck.shouldNotify(new PlatformRelease("1.3", RELEASES), "1.2", ""));
    }

    @Test
    void noAvisaSiYaEstasEnLaUltima() {
        assertFalse(UpdateCheck.shouldNotify(new PlatformRelease("1.2", RELEASES), "1.2", ""));
    }

    /** Mientras no haya release publicada, el servidor manda null y no se avisa. */
    @Test
    void noAvisaSinNadaPublicado() {
        assertFalse(UpdateCheck.shouldNotify(null, "1.2", ""));
    }

    /**
     * Solo https. Un backend comprometido no debe poder mandar al usuario a un
     * file:// local ni a un http:// interceptable.
     */
    @Test
    void rechazaCualquierUrlQueNoSeaHttps() {
        assertFalse(UpdateCheck.shouldNotify(
                new PlatformRelease("1.3", "http://github.com/algo"), "1.2", ""));
        assertFalse(UpdateCheck.shouldNotify(
                new PlatformRelease("1.3", "file:///C:/Windows/System32/cmd.exe"), "1.2", ""));
        assertFalse(UpdateCheck.shouldNotify(
                new PlatformRelease("1.3", "javascript:alert(1)"), "1.2", ""));
        assertFalse(UpdateCheck.shouldNotify(new PlatformRelease("1.3", null), "1.2", ""));
    }

    /** "httpsfalso://" empieza por "https" pero no es el esquema https. */
    @Test
    void noSeConformaConQueLaUrlEmpiecePorLasLetrasHttps() {
        assertFalse(UpdateCheck.shouldNotify(
                new PlatformRelease("1.3", "httpsfalso://github.com"), "1.2", ""));
    }

    @Test
    void noAvisaDeUnaVersionYaDescartada() {
        assertFalse(UpdateCheck.shouldNotify(new PlatformRelease("1.3", RELEASES), "1.2", "1.3"));
    }

    /** Descartar la 1.3 no debe silenciar la 1.4 cuando salga. */
    @Test
    void descartarUnaVersionNoSilenciaLasSiguientes() {
        assertTrue(UpdateCheck.shouldNotify(new PlatformRelease("1.4", RELEASES), "1.2", "1.3"));
    }
}
