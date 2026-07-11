package org.gipsybuho.recetasfamiliares.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {

    private Preferences prefs;
    private ServerConfig config;
    private String previousOverride;

    @BeforeEach
    void setUp() throws BackingStoreException {
        previousOverride = System.getProperty(ServerConfig.SYSTEM_PROPERTY);
        System.clearProperty(ServerConfig.SYSTEM_PROPERTY);
        prefs = Preferences.userRoot().node("recetas-familiares-server-config-test-" + UUID.randomUUID());
        prefs.clear();
        config = new ServerConfig(prefs);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        if (previousOverride != null) {
            System.setProperty(ServerConfig.SYSTEM_PROPERTY, previousOverride);
        } else {
            System.clearProperty(ServerConfig.SYSTEM_PROPERTY);
        }
        if (prefs != null && prefs.nodeExists("")) {
            prefs.removeNode();
            prefs.flush();
        }
    }

    @Test
    void defaultApuntaAProduccion() {
        assertEquals(ServerConfig.DEFAULT_API_BASE_URL, config.baseUrl());
    }

    @Test
    void guardaUrlNormalizada() {
        config.saveBaseUrl("https://EXAMPLE.test:8443");

        assertEquals("https://example.test:8443/", config.baseUrl());
    }

    @Test
    void systemPropertyTienePrecedenciaSobrePreferences() {
        config.saveBaseUrl("https://saved.example.test/");
        System.setProperty(ServerConfig.SYSTEM_PROPERTY, "http://localhost:8080/");

        assertEquals("http://localhost:8080/", config.baseUrl());
    }

    @Test
    void permiteHttpSoloEnHostsDeDesarrollo() {
        assertEquals("http://10.0.2.2:8080/",
                ServerConfig.normalizeAndValidate("http://10.0.2.2:8080"));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.normalizeAndValidate("http://example.test/"));
    }

    @Test
    void rechazaEsquemasYCredencialesPeligrosas() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.normalizeAndValidate("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.normalizeAndValidate("https://user:pass@example.test/"));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.normalizeAndValidate(" https://example.test/"));
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.normalizeAndValidate("https://example.test/api"));
    }
}
