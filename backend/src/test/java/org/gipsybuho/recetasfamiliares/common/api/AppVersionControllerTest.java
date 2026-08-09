package org.gipsybuho.recetasfamiliares.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppVersionControllerTest {

    private static final String RELEASES =
            "https://github.com/GipsyDavy/Recetas-Familiares/releases/latest";

    @Test
    void devuelveLoConfiguradoParaCadaPlataforma() {
        AppVersionController controller =
                new AppVersionController("1.3", RELEASES, "1.1.0", RELEASES);

        var response = controller.appVersion();

        assertThat(response.desktop().latestVersion()).isEqualTo("1.3");
        assertThat(response.desktop().downloadUrl()).isEqualTo(RELEASES);
        assertThat(response.android().latestVersion()).isEqualTo("1.1.0");
    }

    /** Sin configurar, el endpoint responde pero no propone nada. */
    @Test
    void sinConfiguracionCadaPlataformaViajaComoNula() {
        AppVersionController controller = new AppVersionController("", "", "", "");

        var response = controller.appVersion();

        assertThat(response.desktop()).isNull();
        assertThat(response.android()).isNull();
    }

    /** Media configuracion es configuracion rota: no se propone una descarga sin URL. */
    @Test
    void unaPlataformaSinUrlNoSePropone() {
        AppVersionController controller = new AppVersionController("1.3", "", "", "");

        assertThat(controller.appVersion().desktop()).isNull();
    }

    /** Ni una URL sin version, que no se podria comparar con nada. */
    @Test
    void unaPlataformaSinVersionNoSePropone() {
        AppVersionController controller = new AppVersionController("", RELEASES, "", "");

        assertThat(controller.appVersion().desktop()).isNull();
    }

    /** Las plataformas son independientes: que falte una no anula la otra. */
    @Test
    void unaPlataformaConfiguradaYLaOtraNoConvivenSinProblema() {
        AppVersionController controller = new AppVersionController("1.3", RELEASES, "", "");

        var response = controller.appVersion();

        assertThat(response.desktop()).isNotNull();
        assertThat(response.android()).isNull();
    }

    /** Un espacio de mas en una variable de entorno no debe romper la comparacion. */
    @Test
    void recortaLosEspaciosDeLaConfiguracion() {
        AppVersionController controller =
                new AppVersionController("  1.3  ", "  " + RELEASES + "  ", "", "");

        assertThat(controller.appVersion().desktop().latestVersion()).isEqualTo("1.3");
        assertThat(controller.appVersion().desktop().downloadUrl()).isEqualTo(RELEASES);
    }
}
