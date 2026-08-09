package org.gipsybuho.recetasfamiliares.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateNotificationServiceTest {

    /**
     * Regresion real: la primera version pedia "/app-version". La URL base ya
     * termina en "/", asi que salia una doble barra y sin el prefijo api/v1;
     * el backend respondia 401 y el servicio se lo tragaba en silencio. El
     * aviso no aparecia y no habia ni una linea de log que lo explicara.
     */
    @Test
    void laRutaSigueLaConvencionDelRestoDeLlamadas() {
        String path = UpdateNotificationService.ENDPOINT_PATH;

        assertFalse(path.startsWith("/"), "la URL base ya termina en barra");
        assertTrue(path.startsWith("api/v1/"), "falta el prefijo de version de la API");
    }
}
