package org.gipsybuho.recetasfamiliares.data.repository;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyRepositoryHttpTest {

    private Preferences prefs;
    private AppSession session;
    private MockWebServer server;
    private ApiClient client;
    private FamilyRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("recetas-familiares-family-repo-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        session.setTokens("token", "refresh");
        server = new MockWebServer();
        server.start();
        client = new ApiClient(session, server.url("/").toString());
        repository = new FamilyRepository(client, session);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) client.shutdown();
        if (server != null) server.shutdown();
        if (prefs != null && prefs.nodeExists("")) {
            prefs.removeNode();
            prefs.flush();
        }
    }

    @Test
    void createFamilyEnviaNombreYDevuelveFamiliaCreada() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"f9","name":"Nueva","role":"OWNER","avatarUrl":null}
                        """));

        FamilyDtos.FamilyResponse created = repository.createFamily("Nueva");

        assertEquals("f9", created.id());
        assertEquals("OWNER", created.role());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/families", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("\"name\":\"Nueva\""));
    }

    @Test
    void createFamilySinPermisoPropagaApiException403() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("denied"));

        ApiException error = assertThrows(ApiException.class, () -> repository.createFamily("Nueva"));

        assertEquals(403, error.getHttpStatus());
    }
}
