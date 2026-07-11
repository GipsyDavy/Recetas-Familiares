package org.gipsybuho.recetasfamiliares.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlConfigTest {

    @Test
    fun defaultApuntaAProduccion() {
        assertEquals(
            "https://recetas.167.233.213.242.sslip.io/",
            ServerUrlConfig.DEFAULT_API_BASE_URL
        )
    }

    @Test
    fun normalizaHttps() {
        assertEquals(
            "https://example.test:8443/",
            ServerUrlConfig.normalizeAndValidate("https://EXAMPLE.test:8443")
        )
    }

    @Test
    fun permiteHttpSoloParaDesarrollo() {
        assertEquals(
            "http://10.0.2.2:8080/",
            ServerUrlConfig.normalizeAndValidate("http://10.0.2.2:8080")
        )
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlConfig.normalizeAndValidate("http://example.test/")
        }
    }

    @Test
    fun rechazaEsquemasCredencialesEspaciosYRutas() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlConfig.normalizeAndValidate("javascript:alert(1)")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlConfig.normalizeAndValidate("https://user:pass@example.test/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlConfig.normalizeAndValidate(" https://example.test/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlConfig.normalizeAndValidate("https://example.test/api")
        }
    }
}
