package org.gipsybuho.recetasfamiliares.core

import org.gipsybuho.recetasfamiliares.data.remote.dto.PlatformReleaseDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    private val releases = "https://github.com/GipsyDavy/Recetas-Familiares/releases/latest"

    @Test
    fun unaVersionMayorEsMasNueva() {
        assertTrue(AppUpdate.isNewer("1.4", "1.3"))
    }

    /** El caso que rompe la comparacion alfabetica: "1.10" < "1.9" como texto. */
    @Test
    fun comparaNumeroANumeroNoTextoATexto() {
        assertTrue(AppUpdate.isNewer("1.10", "1.9"))
        assertFalse(AppUpdate.isNewer("1.9", "1.10"))
    }

    @Test
    fun laMismaVersionNoEsMasNueva() {
        assertFalse(AppUpdate.isNewer("1.3", "1.3"))
    }

    /** "1.3.1" tiene mas segmentos que "1.3": los que faltan valen cero. */
    @Test
    fun losSegmentosQueFaltanCuentanComoCero() {
        assertTrue(AppUpdate.isNewer("1.3.1", "1.3"))
        assertFalse(AppUpdate.isNewer("1.3", "1.3.0"))
    }

    /** Ante basura, no avisar: mejor callar que dar un aviso falso. */
    @Test
    fun anteUnaVersionIlegibleNoSeAvisa() {
        assertFalse(AppUpdate.isNewer("no-es-una-version", "1.3"))
        assertFalse(AppUpdate.isNewer("1.4", "tampoco"))
        assertFalse(AppUpdate.isNewer(null, "1.3"))
        assertFalse(AppUpdate.isNewer("", "1.3"))
    }

    /** El sufijo "-debug" de las compilaciones de desarrollo no debe romper nada. */
    @Test
    fun unaVersionConSufijoDeDepuracionNoAvisa() {
        assertFalse(AppUpdate.isNewer("1.4", "1.3-debug"))
    }

    @Test
    fun avisaCuandoHayUnaVersionMasNueva() {
        assertTrue(AppUpdate.shouldNotify(PlatformReleaseDto("1.4", releases), "1.3"))
    }

    @Test
    fun noAvisaSinNadaPublicado() {
        assertFalse(AppUpdate.shouldNotify(null, "1.3"))
    }

    /**
     * Solo https. Un backend comprometido no debe poder mandar a la gente a
     * descargar un APK de cualquier sitio por http, interceptable.
     */
    @Test
    fun rechazaCualquierUrlQueNoSeaHttps() {
        assertFalse(AppUpdate.shouldNotify(PlatformReleaseDto("1.4", "http://github.com/x"), "1.3"))
        assertFalse(AppUpdate.shouldNotify(PlatformReleaseDto("1.4", "file:///sdcard/x.apk"), "1.3"))
        assertFalse(AppUpdate.shouldNotify(PlatformReleaseDto("1.4", null), "1.3"))
    }

    /** "httpsfalso://" empieza por "https" y no es https. */
    @Test
    fun noSeConformaConQueLaUrlEmpiecePorLasLetrasHttps() {
        assertFalse(
            AppUpdate.shouldNotify(PlatformReleaseDto("1.4", "httpsfalso://github.com"), "1.3")
        )
    }
}
