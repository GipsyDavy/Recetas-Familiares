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

    @Test
    void loadHistoryPideElCursorYElLimite() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"items":[],"hasMore":false,"nextBefore":null}
                        """));

        repository.loadHistory("c1", "msg-9", 30);

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/c1/messages?limit=30&before=msg-9",
                request.getPath());
    }

    @Test
    void sendEnviaIdYBodyYDevuelveElMensajeCreado() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"m1","conversationId":"c1","authorUserId":"u1","authorDisplayName":"Yo",
                         "body":"Hola","attachments":[],"createdAt":"2026-07-22T10:00:00Z",
                         "updatedAt":"2026-07-22T10:00:00Z","syncVersion":1,"deleted":false}
                        """));

        PrivateChatDtos.PrivateMessage sent = repository.send("c1", "Hola");

        assertEquals("m1", sent.id());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/c1/messages", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("\"body\":\"Hola\""));
    }

    @Test
    void sendVacioLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> repository.send("c1", "   "));
    }

    @Test
    void editLlamaPutConElCuerpoNuevo() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"m1","conversationId":"c1","authorUserId":"u1","authorDisplayName":"Yo",
                         "body":"Editado","attachments":[],"createdAt":"2026-07-22T10:00:00Z",
                         "updatedAt":"2026-07-22T10:05:00Z","syncVersion":2,"deleted":false}
                        """));

        repository.edit("c1", "m1", "Editado");

        RecordedRequest request = server.takeRequest();
        assertEquals("PUT", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/c1/messages/m1", request.getPath());
    }

    @Test
    void deleteLlamaDelete() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"m1","conversationId":"c1","authorUserId":"u1","authorDisplayName":"Yo",
                         "body":null,"attachments":[],"createdAt":"2026-07-22T10:00:00Z",
                         "updatedAt":"2026-07-22T10:06:00Z","syncVersion":3,"deleted":true}
                        """));

        PrivateChatDtos.PrivateMessage deleted = repository.delete("c1", "m1");

        assertTrue(deleted.deleted());
        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/c1/messages/m1", request.getPath());
    }

    @Test
    void clearLlamaAlEndpointDeClear() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        repository.clear("c1");

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/c1/clear", request.getPath());
    }

    @Test
    void exportDevuelveElHistorialCompleto() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"conversationId":"c1","exportedAt":"2026-07-22T10:10:00Z","totalMessages":0,"messages":[]}
                        """));

        var export = repository.export("c1");

        assertEquals("c1", export.conversationId());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/v1/families/fam-1/conversations/c1/export", request.getPath());
    }
}
