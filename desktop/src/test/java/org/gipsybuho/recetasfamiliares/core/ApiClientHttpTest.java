package org.gipsybuho.recetasfamiliares.core;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiClientHttpTest {

    private Preferences prefs;
    private AppSession session;
    private MockWebServer server;
    private ApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("recetas-familiares-api-client-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        server = new MockWebServer();
        server.start();
        client = new ApiClient(session, server.url("/").toString());
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
    void getTras401RefrescaTokenReintentaYGuardaSesion() throws Exception {
        session.setTokens("expired-token", "refresh-1");
        server.enqueue(new MockResponse().setResponseCode(401).setBody("expired"));
        server.enqueue(jsonResponse("""
                {
                  "accessToken": "fresh-token",
                  "refreshToken": "refresh-2",
                  "user": {"id": "u1", "email": "emma@example.test", "displayName": "Emma"},
                  "family": {"id": "fam-1", "name": "Familia"},
                  "familyId": "fam-1"
                }
                """));
        server.enqueue(jsonResponse("""
                {"items": [], "page": 0, "size": 30, "totalItems": 0, "totalPages": 1}
                """));

        RecipeDtos.RecipePageResponse response = client.get("api/v1/recipes", RecipeDtos.RecipePageResponse.class);

        assertEquals(0, response.items().size());
        assertEquals("fresh-token", session.getAccessToken());
        assertEquals("refresh-2", session.getRefreshToken());

        RecordedRequest first = server.takeRequest();
        RecordedRequest refresh = server.takeRequest();
        RecordedRequest retry = server.takeRequest();
        assertEquals("/api/v1/recipes", first.getPath());
        assertEquals("Bearer expired-token", first.getHeader("Authorization"));
        assertEquals("/api/v1/auth/refresh", refresh.getPath());
        assertNull(refresh.getHeader("Authorization"));
        assertEquals("Bearer fresh-token", retry.getHeader("Authorization"));
    }

    @Test
    void getTras401ConRefreshFallidoLimpiaSesion() throws Exception {
        session.setTokens("expired-token", "bad-refresh");
        session.setFamilyId("fam-1");
        server.enqueue(new MockResponse().setResponseCode(401).setBody("expired"));
        server.enqueue(new MockResponse().setResponseCode(401).setBody("bad refresh"));

        ApiException error = assertThrows(
                ApiException.class,
                () -> client.get("api/v1/recipes", RecipeDtos.RecipePageResponse.class)
        );

        assertEquals(401, error.getHttpStatus());
        assertFalse(session.isLoggedIn());
        assertNull(session.getAccessToken());
        assertNull(session.getRefreshToken());
        assertNull(session.getFamilyId());
        assertEquals("/api/v1/recipes", server.takeRequest().getPath());
        assertEquals("/api/v1/auth/refresh", server.takeRequest().getPath());
    }

    @Test
    void fetchImageSoloEnviaAuthorizationAlOrigenDelApi() throws Exception {
        session.setTokens("access-token", "refresh-token");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("api-image"));
        MockWebServer external = new MockWebServer();
        external.start();
        try {
            external.enqueue(new MockResponse().setResponseCode(200).setBody("external-image"));

            byte[] apiBytes = client.fetchImage(server.url("/uploads/recipe.jpg").toString());
            byte[] externalBytes = client.fetchImage(external.url("/uploads/recipe.jpg").toString());

            assertArrayEquals("api-image".getBytes(StandardCharsets.UTF_8), apiBytes);
            assertArrayEquals("external-image".getBytes(StandardCharsets.UTF_8), externalBytes);
            assertEquals("Bearer access-token", server.takeRequest().getHeader("Authorization"));
            assertNull(external.takeRequest().getHeader("Authorization"));
        } finally {
            external.shutdown();
        }
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
