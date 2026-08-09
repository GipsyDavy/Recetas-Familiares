package org.gipsybuho.recetasfamiliares.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppVersionTest {

    @Test
    void unaVersionMayorEsMasNueva() {
        assertTrue(AppVersion.isNewer("1.3", "1.2"));
    }

    /** El caso que rompe la comparacion alfabetica: "1.10" < "1.9" como texto. */
    @Test
    void comparaNumeroANumeroNoTextoATexto() {
        assertTrue(AppVersion.isNewer("1.10", "1.9"));
        assertFalse(AppVersion.isNewer("1.9", "1.10"));
    }

    @Test
    void laMismaVersionNoEsMasNueva() {
        assertFalse(AppVersion.isNewer("1.2", "1.2"));
    }

    @Test
    void unaVersionMasAntiguaNoEsMasNueva() {
        assertFalse(AppVersion.isNewer("1.1", "1.2"));
    }

    /** "1.2.1" tiene mas segmentos que "1.2": los que faltan valen cero. */
    @Test
    void losSegmentosQueFaltanCuentanComoCero() {
        assertTrue(AppVersion.isNewer("1.2.1", "1.2"));
        assertFalse(AppVersion.isNewer("1.2", "1.2.0"));
    }

    /** Ante basura, no avisar: es preferible callar que dar un aviso falso. */
    @Test
    void anteUnaVersionIlegibleNoSeAvisa() {
        assertFalse(AppVersion.isNewer("no-es-una-version", "1.2"));
        assertFalse(AppVersion.isNewer("1.3", "tampoco"));
        assertFalse(AppVersion.isNewer(null, "1.2"));
        assertFalse(AppVersion.isNewer("1.3", null));
        assertFalse(AppVersion.isNewer("", "1.2"));
    }

    /**
     * Ejecutando desde el IDE o desde los tests no hay manifiesto, asi que
     * current() devuelve el valor de reserva. Lo que importa es que nunca sea
     * nulo ni vacio: se compara con lo que diga el servidor.
     */
    @Test
    void laVersionPropiaSiempreEsComparable() {
        String current = AppVersion.current();

        assertTrue(current != null && !current.isBlank());
        assertFalse(AppVersion.isNewer(current, current));
    }
}
