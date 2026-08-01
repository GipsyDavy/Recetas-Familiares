package org.gipsybuho.recetasfamiliares.core;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageCacheTest {

    private static final String PAYLOAD = "bytes-de-la-portada";

    private Preferences prefs;
    private AppSession session;
    private MockWebServer server;
    private ApiClient client;
    private ImageCache cache;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("recetas-familiares-image-cache-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        session.setTokens("token-de-prueba", "refresh-de-prueba");
        server = new MockWebServer();
        server.start();
        client = new ApiClient(session, server.url("/").toString());
        cache = new ImageCache(client, 8, 1024);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
        if (prefs != null && prefs.nodeExists("")) {
            prefs.removeNode();
            prefs.flush();
        }
    }

    @Test
    void descargaLaImagenConElTokenDeLaSesion() throws Exception {
        server.enqueue(new MockResponse().setBody(PAYLOAD));

        byte[] bytes = cache.fetch(server.url("/uploads/portada.jpg").toString());

        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), bytes);
        RecordedRequest request = server.takeRequest();
        assertEquals("Bearer token-de-prueba", request.getHeader("Authorization"));
    }

    @Test
    void cacheaElAciertoParaNoRepetirLaDescarga() throws Exception {
        server.enqueue(new MockResponse().setBody(PAYLOAD));
        String url = server.url("/uploads/portada.jpg").toString();

        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), cache.fetch(url));
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), cache.fetch(url));

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void devuelveNullYCacheaElFalloParaNoMartillearElServidor() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        String url = server.url("/uploads/no-existe.jpg").toString();

        assertNull(cache.fetch(url));
        assertNull(cache.fetch(url));

        assertEquals(1, server.getRequestCount());
    }

    @Test
    void clearCacheObligaAVolverADescargar() throws Exception {
        server.enqueue(new MockResponse().setBody(PAYLOAD));
        server.enqueue(new MockResponse().setBody(PAYLOAD));
        String url = server.url("/uploads/portada.jpg").toString();

        cache.fetch(url);
        cache.clearCache();
        cache.fetch(url);

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void expulsaLoMasViejoCuandoSeAgotaElPresupuestoDeMemoria() throws Exception {
        // El presupuesto es de 1024 bytes: la segunda imagen de 700 no cabe con la primera.
        String grande = "x".repeat(700);
        server.enqueue(new MockResponse().setBody(grande));
        server.enqueue(new MockResponse().setBody(grande));
        server.enqueue(new MockResponse().setBody(grande));
        String primera = server.url("/uploads/primera.jpg").toString();
        String segunda = server.url("/uploads/segunda.jpg").toString();

        cache.fetch(primera);
        cache.fetch(segunda);
        cache.fetch(primera); // La primera ya fue expulsada: vuelve a bajar.

        assertEquals(3, server.getRequestCount());
    }

    @Test
    void devuelveLaImagenQueNoCabeEnElPresupuestoSinCachearla() throws Exception {
        String enorme = "y".repeat(2048);
        server.enqueue(new MockResponse().setBody(enorme));
        server.enqueue(new MockResponse().setBody(enorme));
        String url = server.url("/uploads/enorme.jpg").toString();

        assertArrayEquals(enorme.getBytes(StandardCharsets.UTF_8), cache.fetch(url));
        assertArrayEquals(enorme.getBytes(StandardCharsets.UTF_8), cache.fetch(url));

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void urlNulaOEnBlancoNoLlegaALaRed() throws Exception {
        assertNull(cache.fetch(null));
        assertNull(cache.fetch("   "));

        assertEquals(0, server.getRequestCount());
    }
}
