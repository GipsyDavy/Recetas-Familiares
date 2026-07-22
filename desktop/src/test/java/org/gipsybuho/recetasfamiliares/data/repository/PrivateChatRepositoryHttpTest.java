package org.gipsybuho.recetasfamiliares.data.repository;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class PrivateChatRepositoryHttpTest {

    private Preferences prefs;
    private AppSession session;
    private MockWebServer server;
    private ApiClient client;
    private PrivateChatRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("recetas-familiares-dm-repo-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        session.setTokens("token", "refresh");
        session.setFamilyId("fam-1");
        server = new MockWebServer();
        server.start();
        client = new ApiClient(session, server.url("/").toString());
        repository = new PrivateChatRepository(client, session);
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
    void listConversationsDevuelveLaBandejaOrdenadaPorActividad() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [{"conversationId":"c1","otherUserId":"u2","otherUserDisplayName":"Ana",
                          "otherUserAvatarUrl":null,"lastMessagePreview":"Hola","lastMessageAt":"2026-07-22T10:00:00Z"}]
                        """));

        var conversations = repository.listConversations();

        assertEquals(1, conversations.length);
        assertEquals("c1", conversations[0].conversationId());
        assertEquals("Ana", conversations[0].otherUserDisplayName());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations", request.getPath());
    }

    @Test
    void createOrGetConversationLlamaAlEndpointWithYDevuelveLaConversacion() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"conversationId":"c1","otherUserId":"u2","otherUserDisplayName":"Ana",
                         "otherUserAvatarUrl":null,"lastMessagePreview":null,"lastMessageAt":null}
                        """));

        PrivateChatDtos.PrivateConversation created = repository.createOrGetConversation("u2");

        assertEquals("c1", created.conversationId());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/with/u2", request.getPath());
    }

    @Test
    void listConversationsSinFamiliaLanzaIllegalState() {
        session.setFamilyId(null);

        assertThrows(IllegalStateException.class, () -> repository.listConversations());
    }

    @Test
    void createOrGetConversationPropagaApiException404SiNoEsMiembro() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        ApiException error = assertThrows(ApiException.class,
                () -> repository.createOrGetConversation("stranger"));

        assertEquals(404, error.getHttpStatus());
    }
}
