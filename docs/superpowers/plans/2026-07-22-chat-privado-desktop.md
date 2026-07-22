# Chat Privado Desktop — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Desktop (JavaFX) client for the already-shipped private 1:1 chat backend — conversation inbox, embedded chat panel, "Mensaje" entry point from Miembros, and an unread badge, per `docs/superpowers/specs/2026-07-19-chat-privado-design.md` (+ addendum 2026-07-22).

**Architecture:** New `PrivateChatDtos`/`PrivateChatRepository` mirror the existing `ChatDtos`/`ChatRepository` pattern for REST. The existing family `ChatSocket` (one persistent WebSocket per login session) is extended — not duplicated — to also subscribe to the user's own inbox topic (fixed, for the badge) and to one conversation topic at a time (dynamic, as the user browses conversations), per the spec's explicit decision to reuse the connection. `ChatRepository` gains the inbox/unread state (mirrors how it already owns presence state) and exposes the shared socket so the new `ConversationsView`/`PrivateChatView` can drive it. `FamilyMembersView` becomes visible to all roles (its admin-only toolbar is unaffected) and gains a per-row "Mensaje" button.

**Tech Stack:** Java 21, JavaFX 21, OkHttp (WebSocket + MockWebServer for tests), Gson, JUnit 5.

---

## File Manifest

| File | Change |
|---|---|
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/PrivateChatDtos.java` | Create |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.java` | Create |
| `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryHttpTest.java` | Create |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java` | Modify |
| `desktop/src/test/java/org/gipsybuho/recetasfamiliares/api/ChatSocketFrameParsingTest.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContext.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/PrivateChatView.java` | Create |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/ConversationsView.java` | Create |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java` | Modify |
| `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java` | Modify |

---

### Task 1: Client DTOs — `PrivateChatDtos.java`

**Files:**
- Create: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/PrivateChatDtos.java`

Mirrors `ChatDtos.java`, adapted to the backend `dm/` contract already verified (`PrivateConversationResponse`, `PrivateMessageResponse`, `PrivateMessageAttachmentResponse`, `PrivateMessageHistoryResponse`, `PrivateMessageExportResponse`, `SendPrivateMessageRequest`, `EditPrivateMessageRequest`, `PrivateInboxPing`).

- [ ] **Step 1: Write the file**

```java
package org.gipsybuho.recetasfamiliares.api.dto;

import java.util.List;

/**
 * DTOs del chat privado 1:1. Reflejan el contrato REST/WS del backend bajo
 * {@code /api/v1/families/{familyId}/conversations} (paquete backend {@code dm}).
 * Las marcas de tiempo se modelan como String ISO-8601 (UTC), igual que ChatDtos.
 */
public final class PrivateChatDtos {

    private PrivateChatDtos() {}

    public record PrivateAttachment(
            String id,
            String url,
            String thumbnailUrl,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height
    ) {
    }

    public record PrivateMessage(
            String id,
            String conversationId,
            String authorUserId,
            String authorDisplayName,
            String body,
            List<PrivateAttachment> attachments,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {
        public List<PrivateAttachment> attachmentsOrEmpty() {
            return attachments != null ? attachments : List.of();
        }

        /** Descarta frames WS incompletos o mal formados antes de mostrarlos. */
        public boolean isUsable() {
            return notBlank(id)
                    && notBlank(conversationId)
                    && notBlank(authorUserId)
                    && notBlank(authorDisplayName)
                    && notBlank(createdAt);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** Fila de la bandeja: la conversacion vista desde el usuario que la solicita. */
    public record PrivateConversation(
            String conversationId,
            String otherUserId,
            String otherUserDisplayName,
            String otherUserAvatarUrl,
            String lastMessagePreview,
            String lastMessageAt
    ) {
    }

    /** Pagina de historial por cursor. {@code items} viene descendente (mas reciente primero). */
    public record PrivateMessageHistory(
            List<PrivateMessage> items,
            boolean hasMore,
            String nextBefore
    ) {
    }

    /** Exportacion de una conversacion para el usuario que la solicita, orden ascendente. */
    public record PrivateMessageExport(
            String conversationId,
            String exportedAt,
            int totalMessages,
            List<PrivateMessage> messages
    ) {
    }

    /** Envio de mensaje de texto. El cliente genera el {@code id} (UUID v4) para idempotencia. */
    public record SendPrivateMessageRequest(
            String id,
            String body
    ) {
    }

    /** Edicion de mensaje propio dentro de la ventana permitida por backend. */
    public record EditPrivateMessageRequest(
            String body
    ) {
    }

    /** Ping ligero del topic de bandeja: nunca lleva el cuerpo del mensaje. */
    public record PrivateInboxPing(
            String conversationId,
            String senderUserId,
            String sentAt
    ) {
    }
}
```

- [ ] **Step 2: Compile to verify no syntax errors**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/dto/PrivateChatDtos.java
git commit -m "feat(desktop): DTOs del chat privado (mirror de ChatDtos)"
```

---

### Task 2: `PrivateChatRepository` — listar y crear conversaciones

**Files:**
- Create: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.java`
- Test: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryHttpTest.java`

- [ ] **Step 1: Write the failing tests** (MockWebServer pattern, same as `FamilyRepositoryHttpTest`)

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -f desktop/pom.xml test -Dtest=PrivateChatRepositoryHttpTest`
Expected: compile error — `PrivateChatRepository` does not exist yet.

- [ ] **Step 3: Write `PrivateChatRepository` (conversations part only)**

```java
package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

/**
 * Chat privado 1:1 fase Desktop: envio/historial por REST, tiempo real via el
 * ChatSocket compartido de {@link ChatRepository} (una sola conexion WS por
 * sesion). Backend valida ownership de conversacion y de familia en cada
 * operacion.
 */
public class PrivateChatRepository {

    public static final int MAX_BODY_LENGTH = 2_000;
    public static final int PAGE_SIZE = 30;

    private final ApiClient api;
    private final AppSession session;

    public PrivateChatRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    public PrivateChatDtos.PrivateConversation[] listConversations() throws ApiException {
        return api.get("api/v1/families/" + requireFamily() + "/conversations",
                PrivateChatDtos.PrivateConversation[].class);
    }

    public PrivateChatDtos.PrivateConversation createOrGetConversation(String otherUserId) throws ApiException {
        return api.post("api/v1/families/" + requireFamily() + "/conversations/with/" + otherUserId,
                "{}", PrivateChatDtos.PrivateConversation.class);
    }

    private String requireFamily() {
        String family = session.getFamilyId();
        if (family == null || family.isBlank()) {
            throw new IllegalStateException("No hay familia en la sesion");
        }
        return family;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -f desktop/pom.xml test -Dtest=PrivateChatRepositoryHttpTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryHttpTest.java
git commit -m "feat(desktop): PrivateChatRepository - listar y crear conversaciones"
```

---

### Task 3: `PrivateChatRepository` — historial, envio, edicion, borrado, export

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.java`
- Modify: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryHttpTest.java`

- [ ] **Step 1: Add failing tests** (append to the test class from Task 2, before the closing `}`)

```java
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
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -f desktop/pom.xml test -Dtest=PrivateChatRepositoryHttpTest`
Expected: compile error — methods don't exist yet on `PrivateChatRepository`.

- [ ] **Step 3: Add the methods** (insert into `PrivateChatRepository`, after `createOrGetConversation`, before `requireFamily`)

```java
    public PrivateChatDtos.PrivateMessageHistory loadHistory(String conversationId, String before, int limit)
            throws ApiException {
        StringBuilder path = new StringBuilder("api/v1/families/")
                .append(requireFamily())
                .append("/conversations/").append(conversationId)
                .append("/messages?limit=").append(limit);
        if (before != null && !before.isBlank()) {
            path.append("&before=").append(before.trim());
        }
        return api.get(path.toString(), PrivateChatDtos.PrivateMessageHistory.class);
    }

    public PrivateChatDtos.PrivateMessage send(String conversationId, String body) throws ApiException {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        var request = new PrivateChatDtos.SendPrivateMessageRequest(
                java.util.UUID.randomUUID().toString(), text);
        return api.post("api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/messages",
                request, PrivateChatDtos.PrivateMessage.class);
    }

    public PrivateChatDtos.PrivateMessage sendImage(String conversationId, String body, java.io.File image)
            throws ApiException {
        String text = body == null ? "" : body.trim();
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        if (image == null || !image.isFile()) {
            throw new IllegalArgumentException("Selecciona una imagen valida");
        }
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("id", java.util.UUID.randomUUID().toString());
        if (!text.isEmpty()) {
            fields.put("body", text);
        }
        return api.postMultipart(
                "api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/messages/images",
                fields, java.util.List.of(image), "files", PrivateChatDtos.PrivateMessage.class);
    }

    public PrivateChatDtos.PrivateMessage edit(String conversationId, String messageId, String body)
            throws ApiException {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        if (text.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo");
        }
        var request = new PrivateChatDtos.EditPrivateMessageRequest(text);
        return api.put("api/v1/families/" + requireFamily() + "/conversations/" + conversationId
                + "/messages/" + messageId, request, PrivateChatDtos.PrivateMessage.class);
    }

    public PrivateChatDtos.PrivateMessage delete(String conversationId, String messageId) throws ApiException {
        return api.delete("api/v1/families/" + requireFamily() + "/conversations/" + conversationId
                + "/messages/" + messageId, PrivateChatDtos.PrivateMessage.class);
    }

    public void clear(String conversationId) throws ApiException {
        api.post("api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/clear",
                "{}", Void.class);
    }

    public PrivateChatDtos.PrivateMessageExport export(String conversationId) throws ApiException {
        return api.get("api/v1/families/" + requireFamily() + "/conversations/" + conversationId + "/export",
                PrivateChatDtos.PrivateMessageExport.class);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -f desktop/pom.xml test -Dtest=PrivateChatRepositoryHttpTest`
Expected: `Tests run: 11, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepository.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/data/repository/PrivateChatRepositoryHttpTest.java
git commit -m "feat(desktop): PrivateChatRepository - historial, envio, edicion, borrado, export"
```

---

### Task 4: Extender `ChatSocket` — topic de inbox y de conversacion

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java`
- Modify: `desktop/src/test/java/org/gipsybuho/recetasfamiliares/api/ChatSocketFrameParsingTest.java`

**Context (current code, for reference):** `ChatSocket`'s constructor takes `(apiClient, tokenSupplier, familyId, gson, onMessage, onConnectionChange, onPresenceUpdate)`, builds fixed `topic`/`presenceTopic` fields, subscribes both in the `CONNECTED` frame handler, and routes `MESSAGE` frames by comparing `destination` against `presenceTopic` (else falls through to chat). This task adds a fixed inbox topic (always subscribed) and a dynamic, switchable conversation topic — **without changing the existing constructor's chat/presence behavior**, so `ChatRepository`'s current call site and the 33 passing desktop tests are unaffected.

- [ ] **Step 1: Add failing routing tests** (append to `ChatSocketFrameParsingTest`, before the closing `}`)

```java
    @Test
    void extractsDestinationFromInboxFrame() {
        String frame = "MESSAGE\n"
                + "destination:/topic/users/u1/inbox\n"
                + "subscription:sub-inbox\n"
                + "\n"
                + "{\"conversationId\":\"c1\",\"senderUserId\":\"u2\",\"sentAt\":\"2026-07-22T10:00:00Z\"}";

        assertEquals("/topic/users/u1/inbox", ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void extractsDestinationFromConversationFrame() {
        String frame = "MESSAGE\n"
                + "destination:/topic/conversations/c1\n"
                + "subscription:sub-conversation\n"
                + "\n"
                + "{\"id\":\"m1\"}";

        assertEquals("/topic/conversations/c1", ChatSocket.extractHeader(frame, "destination"));
    }
```

These only exercise the already-existing, already-tested `extractHeader` helper against the new topic shapes — they pass immediately and just document the frame format the new routing (Step 3) depends on. The real routing behavior is covered by the manual/functional check in Task 12 (this class has no WebSocket integration test today — `ChatSocketFrameParsingTest` only covers frame parsing, matching the existing test's own scope).

- [ ] **Step 2: Run to verify they pass already**

Run: `mvn -f desktop/pom.xml test -Dtest=ChatSocketFrameParsingTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0` (2 new + 3 existing)

- [ ] **Step 3: Extend `ChatSocket.java`**

Modify the constructor and fields (replace the existing constructor and field block):

```java
    private final ApiClient apiClient;
    private final Supplier<String> tokenSupplier;
    private final String familyId;
    private final String wsUrl;
    private final String topic;
    private final String presenceTopic;
    private final String inboxTopic;
    private final Gson gson;
    private final Consumer<ChatDtos.ChatMessage> onMessage;
    private final Consumer<Boolean> onConnectionChange;
    private final Consumer<Set<String>> onPresenceUpdate;
    private final Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateInboxPing> onInboxPing;
    private final Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> onPrivateMessage;

    // Conversacion privada actualmente seleccionada (o null); mutable porque el
    // usuario cambia de conversacion sin reabrir el WebSocket.
    private volatile String currentConversationId;
    private volatile String currentConversationTopic;

    public ChatSocket(
            ApiClient apiClient,
            Supplier<String> tokenSupplier,
            String familyId,
            String myUserId,
            Gson gson,
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange,
            Consumer<Set<String>> onPresenceUpdate,
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateInboxPing> onInboxPing,
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> onPrivateMessage
    ) {
        this.apiClient = apiClient;
        this.tokenSupplier = tokenSupplier;
        this.familyId = familyId;
        this.wsUrl = toWebSocketUrl(apiClient.getBaseUrl());
        this.topic = "/topic/families/" + familyId + "/chat";
        this.presenceTopic = "/topic/families/" + familyId + "/presence";
        this.inboxTopic = "/topic/users/" + myUserId + "/inbox";
        this.gson = gson;
        this.onMessage = onMessage;
        this.onConnectionChange = onConnectionChange;
        this.onPresenceUpdate = onPresenceUpdate;
        this.onInboxPing = onInboxPing;
        this.onPrivateMessage = onPrivateMessage;
    }
```

Add two public methods (near `connect()`/`disconnect()`):

```java
    /**
     * Se suscribe al topic de una conversacion privada, sustituyendo cualquier
     * suscripcion anterior. Sin efecto si el socket no esta conectado todavia
     * (se reintenta en el proximo CONNECTED via {@code currentConversationId}).
     */
    public synchronized void subscribeConversation(String conversationId) {
        unsubscribeConversationFrame();
        this.currentConversationId = conversationId;
        this.currentConversationTopic = conversationId != null
                ? "/topic/conversations/" + conversationId : null;
        if (conversationId != null) {
            sendConversationSubscribe();
        }
    }

    /** Cancela la suscripcion a la conversacion actual, si hay alguna. */
    public synchronized void unsubscribeConversation() {
        unsubscribeConversationFrame();
        this.currentConversationId = null;
        this.currentConversationTopic = null;
    }

    private void sendConversationSubscribe() {
        WebSocket socket = this.webSocket;
        if (socket == null || currentConversationTopic == null) {
            return;
        }
        socket.send("SUBSCRIBE\n"
                + "id:sub-conversation\n"
                + "destination:" + currentConversationTopic + "\n"
                + "\n"
                + NUL);
    }

    private void unsubscribeConversationFrame() {
        WebSocket socket = this.webSocket;
        if (socket == null || currentConversationTopic == null) {
            return;
        }
        socket.send("UNSUBSCRIBE\n"
                + "id:sub-conversation\n"
                + "\n"
                + NUL);
    }
```

Modify the `CONNECTED` case inside `handleFrame` (add inbox subscribe + conversation re-subscribe after the existing presence subscribe, before `reconnectAttempt = 0;`):

```java
            case "CONNECTED" -> {
                String subscribeChat = "SUBSCRIBE\n"
                        + "id:sub-chat\n"
                        + "destination:" + topic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribeChat);
                String subscribePresence = "SUBSCRIBE\n"
                        + "id:sub-presence\n"
                        + "destination:" + presenceTopic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribePresence);
                String subscribeInbox = "SUBSCRIBE\n"
                        + "id:sub-inbox\n"
                        + "destination:" + inboxTopic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribeInbox);
                if (currentConversationTopic != null) {
                    sendConversationSubscribe();
                }
                reconnectAttempt = 0;
                onConnectionChange.accept(true);
            }
```

Modify the `MESSAGE` case (replace the `if (presenceTopic.equals(destination))` block):

```java
            case "MESSAGE" -> {
                String destination = extractHeader(frame, "destination");
                int split = frame.indexOf("\n\n");
                String body = split >= 0 ? frame.substring(split + 2).trim() : "";
                if (body.isEmpty()) {
                    // no-op
                } else if (presenceTopic.equals(destination)) {
                    handlePresenceMessage(body);
                } else if (inboxTopic.equals(destination)) {
                    handleInboxPing(body);
                } else if (destination != null && destination.equals(currentConversationTopic)) {
                    handlePrivateMessage(body);
                } else if (topic.equals(destination)) {
                    handleChatMessage(body);
                }
            }
```

Add the two new handlers (near `handlePresenceMessage`):

```java
    private void handleInboxPing(String body) {
        try {
            var ping = gson.fromJson(body,
                    org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateInboxPing.class);
            if (ping != null && ping.conversationId() != null) {
                onInboxPing.accept(ping);
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }

    private void handlePrivateMessage(String body) {
        try {
            var message = gson.fromJson(body,
                    org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage.class);
            if (message != null && message.isUsable()) {
                onPrivateMessage.accept(message);
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }
```

- [ ] **Step 4: Compile**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: compile error in `ChatRepository.java` (constructor call site now has the wrong arity) — expected, fixed in Task 5.

- [ ] **Step 5: Commit** (deferred to end of Task 5, since the module won't compile until then)

---

### Task 5: `ChatRepository` — estado de bandeja/no-leidos y socket compartido

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java`

**Context (current code):** `ChatRepository` already owns presence state this same way — `lastOnlineUserIds` (AtomicReference), `presenceListener` (volatile Consumer), `handlePresenceUpdate` (private, called by `ChatSocket`'s `onPresenceUpdate`), `setPresenceListener` (public). This task mirrors that exact pattern for inbox/unread state, plus stores the socket it builds so other views can drive `subscribeConversation`/`unsubscribeConversation`.

- [ ] **Step 1: Add fields** (after the existing `lastOnlineUserIds`/`presenceListener` fields)

```java
    private final java.util.Map<String, Integer> unreadByConversation = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String activeConversationId;
    private volatile java.util.function.Consumer<java.util.Map<String, Integer>> inboxListener;
    private volatile java.util.function.Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> conversationMessageListener;
    private volatile ChatSocket activeSocket;
```

- [ ] **Step 2: Add the public API** (after `setPresenceListener`/`lastOnlineUserIds()`)

```java
    /** Socket compartido de esta sesion de chat (familiar + privado). Null si no hay ninguna conexion abierta. */
    public ChatSocket activeSocket() {
        return activeSocket;
    }

    /** Snapshot inmutable de no-leidos por conversacion, para pintar la bandeja/badge. */
    public Map<String, Integer> unreadByConversation() {
        return Map.copyOf(unreadByConversation);
    }

    /**
     * Marca una conversacion como la que el usuario esta viendo ahora mismo:
     * limpia su contador de no-leidos y evita que nuevos pings la vuelvan a
     * marcar mientras siga activa. Pasar {@code null} cuando no hay ninguna
     * conversacion abierta (p.ej. al salir de Conversaciones).
     */
    public void setActiveConversation(String conversationId) {
        this.activeConversationId = conversationId;
        if (conversationId != null && unreadByConversation.remove(conversationId) != null) {
            notifyInboxListener();
        }
    }

    public void setInboxListener(Consumer<Map<String, Integer>> listener) {
        this.inboxListener = listener;
    }

    /** Recibe los mensajes en vivo de la conversacion actualmente suscrita en el socket. */
    public void setConversationMessageListener(
            Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> listener) {
        this.conversationMessageListener = listener;
    }

    private void handleInboxPing(org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateInboxPing ping) {
        if (ping.conversationId().equals(activeConversationId)) {
            return;
        }
        unreadByConversation.merge(ping.conversationId(), 1, Integer::sum);
        notifyInboxListener();
    }

    private void handlePrivateMessage(org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage message) {
        Consumer<org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos.PrivateMessage> listener = conversationMessageListener;
        if (listener != null) {
            listener.accept(message);
        }
    }

    private void notifyInboxListener() {
        Consumer<Map<String, Integer>> listener = inboxListener;
        if (listener != null) {
            listener.accept(unreadByConversation());
        }
    }
```

Add the missing import at the top of the file: `import java.util.Map;` (the class already imports `java.util.function.Consumer`, `java.util.Set`, etc. — check the existing import block and only add `Map` if not already present).

- [ ] **Step 3: Wire the new `ChatSocket` constructor params in `openRealtime()`** (replace the existing method body)

```java
    public ChatSocket openRealtime(
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange
    ) {
        String family = familyId();
        if (family == null || family.isBlank()) {
            return null;
        }
        ChatSocket socket = new ChatSocket(
                api,
                session::getAccessToken,
                family,
                session.getUserId(),
                gson,
                onMessage,
                onConnectionChange,
                this::handlePresenceUpdate,
                this::handleInboxPing,
                this::handlePrivateMessage);
        socket.connect();
        this.activeSocket = socket;
        return socket;
    }
```

- [ ] **Step 4: Compile and run the full desktop suite**

Run: `mvn -f desktop/pom.xml test`
Expected: `BUILD SUCCESS`, same test count as before Task 4 plus the 2 new `ChatSocketFrameParsingTest` cases, 0 failures. This confirms the extension didn't regress the family chat/presence path.

- [ ] **Step 5: Commit** (Tasks 4 + 5 together, since the module only compiles once both are in)

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/api/ChatSocket.java desktop/src/test/java/org/gipsybuho/recetasfamiliares/api/ChatSocketFrameParsingTest.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/data/repository/ChatRepository.java
git commit -m "feat(desktop): extiende ChatSocket/ChatRepository con inbox y conversacion privada"
```

---

### Task 6: Cablear `PrivateChatRepository` en `AppContext`

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContext.java`

- [ ] **Step 1: Add the import, field, constructor line and getter**

Add import: `import org.gipsybuho.recetasfamiliares.data.repository.PrivateChatRepository;`

Add field (after `private final ChatRepository chatRepository;`):
```java
    private final PrivateChatRepository privateChatRepository;
```

Add construction (after `chatRepository = new ChatRepository(apiClient, session);`):
```java
        privateChatRepository = new PrivateChatRepository(apiClient, session);
```

Add getter (after `public ChatRepository getChatRepository() { return chatRepository; }`):
```java
    public PrivateChatRepository getPrivateChatRepository() { return privateChatRepository; }
```

- [ ] **Step 2: Compile**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/core/AppContext.java
git commit -m "feat(desktop): expone PrivateChatRepository desde AppContext"
```

---

### Task 7: `PrivateChatView` — panel de una conversacion

**Files:**
- Create: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/PrivateChatView.java`

Mirrors `ChatView.java` (historial paginado, enviar texto/imagen, editar/borrar propio, exportar, borrar-para-mi, adjuntos), scoped to one `conversationId` instead of the whole family, with **no own `ChatSocket`** — it drives the shared one via `ChatRepository` (`setConversationMessageListener`, `subscribeConversation`/`unsubscribeConversation`, `setActiveConversation`) instead of `ChatView`'s `startRealtime()`/`socket` field. It is a sub-panel embedded in `ConversationsView` (Task 8), **not** a standalone navigable view — per the spec addendum, it extends `VBox`, not `ScrollPane`.

- [ ] **Step 1: Write the file**

```java
package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.data.repository.PrivateChatRepository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel de una conversacion privada 1:1, embebido en {@link ConversationsView}.
 * Mismo patron que {@link ChatView} pero sin ChatSocket propio: la conexion en
 * tiempo real es la compartida de {@code ChatRepository} (una sola por sesion).
 */
public class PrivateChatView extends VBox {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final double NEAR_BOTTOM_THRESHOLD = 0.95;
    private static final ButtonType SAVE_BUTTON_TYPE =
            new ButtonType("Guardar", ButtonBar.ButtonData.OTHER);

    private final AppContext context;
    private final Consumer<String> onStatus;

    private final Label headerLabel = new Label();
    private final Label statusLabel = new Label();
    private final VBox messagesBox = new VBox(8);
    private final ScrollPane scrollPane = new ScrollPane();
    private final Button loadOlderBtn = new Button("Cargar mensajes anteriores");
    private final Label emptyState = buildEmptyState();
    private final TextArea input = new TextArea();
    private final Button attachBtn = new Button("Imagen");
    private final Button sendBtn = new Button("Enviar");
    private final Label charCounter = new Label();

    private final List<PrivateChatDtos.PrivateMessage> messages = new ArrayList<>();

    private String conversationId;
    private boolean loading = false;
    private boolean sending = false;
    private boolean hasMoreOlder = false;
    private String nextBefore = null;
    private String myUserId;

    public PrivateChatView(AppContext context, Consumer<String> onStatus) {
        this.context = context;
        this.onStatus = onStatus;
        this.myUserId = context.getSession().getUserId();
        build();
        showEmpty();
    }

    private void build() {
        getStyleClass().add("chat-view");
        setPadding(new Insets(24));
        setSpacing(0);

        headerLabel.getStyleClass().add("view-header");
        statusLabel.getStyleClass().add("chat-status");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        MenuButton menu = new MenuButton("Opciones");
        menu.getStyleClass().add("action-button-secondary");
        MenuItem exportItem = new MenuItem("Exportar chat");
        exportItem.setOnAction(e -> exportChat());
        MenuItem clearItem = new MenuItem("Borrar chat para mi");
        clearItem.setOnAction(e -> confirmClear());
        menu.getItems().addAll(exportItem, clearItem);

        HBox headerRow = new HBox(12, headerLabel, statusLabel, headerSpacer, menu);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(0, 0, 16, 0));

        loadOlderBtn.getStyleClass().add("action-button-secondary");
        loadOlderBtn.setMaxWidth(Double.MAX_VALUE);
        loadOlderBtn.setVisible(false);
        loadOlderBtn.setManaged(false);
        loadOlderBtn.setOnAction(e -> loadOlder());

        messagesBox.getStyleClass().add("chat-messages");
        messagesBox.setPadding(new Insets(4, 8, 4, 8));
        messagesBox.setFillWidth(true);

        VBox scrollContent = new VBox(8, loadOlderBtn, messagesBox);
        VBox.setVgrow(messagesBox, Priority.ALWAYS);

        scrollPane.setContent(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMinHeight(0);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        input.setPromptText("Escribe un mensaje...");
        input.setWrapText(true);
        input.setPrefRowCount(1);
        input.setMaxHeight(120);
        input.getStyleClass().add("chat-input");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::handleInputKey);
        input.textProperty().addListener((obs, old, val) -> updateCharCounter(val));

        attachBtn.getStyleClass().add("action-button-secondary");
        attachBtn.setOnAction(e -> chooseAndSendImage());

        sendBtn.getStyleClass().add("action-button-primary");
        sendBtn.setDefaultButton(false);
        sendBtn.setOnAction(e -> sendMessage());

        HBox inputRow = new HBox(10, attachBtn, input, sendBtn);
        inputRow.setAlignment(Pos.BOTTOM_LEFT);
        inputRow.setPadding(new Insets(12, 0, 0, 0));

        charCounter.getStyleClass().add("chat-char-counter");
        charCounter.setVisible(false);
        charCounter.setManaged(false);
        HBox counterRow = new HBox(charCounter);
        counterRow.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(headerRow, scrollPane, inputRow, counterRow);
    }

    private Label buildEmptyState() {
        Label label = new Label("Escribe el primer mensaje para empezar la conversacion.");
        label.getStyleClass().add("chat-empty");
        label.setWrapText(true);
        label.setMaxWidth(420);
        label.setAlignment(Pos.CENTER);
        VBox.setMargin(label, new Insets(48, 24, 48, 24));
        return label;
    }

    // ── Ciclo de vida (invocado desde ConversationsView) ───────────────────────

    public void showEmpty() {
        this.conversationId = null;
        headerLabel.setText("Selecciona una conversacion");
        statusLabel.setText("");
        messages.clear();
        renderMessages();
        setDisableComposer(true);
    }

    /** Abre esta conversacion: suscribe el socket compartido y carga el historial. */
    public void open(PrivateChatDtos.PrivateConversation conversation) {
        context.getChatRepository().setConversationMessageListener(this::onRealtimeMessage);
        var socket = context.getChatRepository().activeSocket();
        if (socket != null) {
            socket.subscribeConversation(conversation.conversationId());
        }
        context.getChatRepository().setActiveConversation(conversation.conversationId());

        this.conversationId = conversation.conversationId();
        headerLabel.setText(conversation.otherUserDisplayName() != null
                ? conversation.otherUserDisplayName() : "Conversacion");
        statusLabel.setText("");
        messages.clear();
        hasMoreOlder = false;
        nextBefore = null;
        setDisableComposer(false);
        loadInitialHistory();
    }

    /** Se llama al cerrar Conversaciones o cambiar de familia: libera el socket compartido. */
    public void close() {
        var socket = context.getChatRepository().activeSocket();
        if (socket != null) {
            socket.unsubscribeConversation();
        }
        context.getChatRepository().setActiveConversation(null);
        context.getChatRepository().setConversationMessageListener(null);
    }

    private void setDisableComposer(boolean disable) {
        input.setDisable(disable);
        attachBtn.setDisable(disable);
        sendBtn.setDisable(disable);
    }

    // ── Carga de historial ─────────────────────────────────────────────────────

    private void loadInitialHistory() {
        if (loading || conversationId == null) {
            return;
        }
        String targetConversation = conversationId;
        loading = true;
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessageHistory page =
                        privateChatRepository().loadHistory(targetConversation, null, PrivateChatRepository.PAGE_SIZE);
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    loading = false;
                    messages.clear();
                    List<PrivateChatDtos.PrivateMessage> items = page.items() != null ? page.items() : List.of();
                    for (int i = items.size() - 1; i >= 0; i--) {
                        upsertAscending(items.get(i));
                    }
                    hasMoreOlder = page.hasMore();
                    nextBefore = page.nextBefore();
                    updateLoadOlderBtn();
                    renderMessages();
                    statusLabel.setText("");
                    scrollToBottom();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loading = false;
                    statusLabel.setText("Error al cargar el chat");
                    onStatus.accept("No se pudo cargar la conversacion: " + ex.getMessage());
                });
            }
        });
    }

    private void loadOlder() {
        if (loading || !hasMoreOlder || nextBefore == null || conversationId == null) {
            return;
        }
        String targetConversation = conversationId;
        loading = true;
        loadOlderBtn.setDisable(true);
        final String before = nextBefore;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessageHistory page =
                        privateChatRepository().loadHistory(targetConversation, before, PrivateChatRepository.PAGE_SIZE);
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    loading = false;
                    loadOlderBtn.setDisable(false);
                    List<PrivateChatDtos.PrivateMessage> items = page.items() != null ? page.items() : List.of();
                    boolean changed = false;
                    for (int i = items.size() - 1; i >= 0; i--) {
                        changed |= upsertAscending(items.get(i));
                    }
                    hasMoreOlder = page.hasMore();
                    nextBefore = page.nextBefore();
                    updateLoadOlderBtn();
                    if (changed) {
                        renderMessages();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loading = false;
                    loadOlderBtn.setDisable(false);
                    onStatus.accept("No se pudieron cargar mensajes anteriores.");
                });
            }
        });
    }

    // ── Tiempo real ─────────────────────────────────────────────────────────────

    private void onRealtimeMessage(PrivateChatDtos.PrivateMessage message) {
        if (message == null || message.id() == null || !message.conversationId().equals(conversationId)) {
            return;
        }
        boolean nearBottom = scrollPane.getVvalue() >= NEAR_BOTTOM_THRESHOLD;
        if (upsertAscending(message)) {
            renderMessages();
            boolean mine = isMine(message);
            if (mine || nearBottom) {
                scrollToBottom();
            }
        }
    }

    private boolean upsertAscending(PrivateChatDtos.PrivateMessage message) {
        if (message == null || message.id() == null) {
            return false;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (message.id().equals(messages.get(i).id())) {
                messages.set(i, message);
                sortMessages();
                return true;
            }
        }
        messages.add(message);
        sortMessages();
        return true;
    }

    private void sortMessages() {
        messages.sort(Comparator
                .comparing(PrivateChatDtos.PrivateMessage::createdAt, Comparator.nullsLast(String::compareTo))
                .thenComparing(PrivateChatDtos.PrivateMessage::id, Comparator.nullsLast(String::compareTo)));
    }

    // ── Envio ────────────────────────────────────────────────────────────────────

    private void handleInputKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            sendMessage();
        }
    }

    private void sendMessage() {
        if (sending || conversationId == null) {
            return;
        }
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > PrivateChatRepository.MAX_BODY_LENGTH) {
            onStatus.accept("El mensaje supera el limite de " + PrivateChatRepository.MAX_BODY_LENGTH + " caracteres.");
            return;
        }
        String targetConversation = conversationId;
        setSending(true);
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage saved = privateChatRepository().send(targetConversation, text);
                Platform.runLater(() -> {
                    setSending(false);
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    input.clear();
                    if (upsertAscending(saved)) {
                        renderMessages();
                        scrollToBottom();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSending(false);
                    onStatus.accept("No se pudo enviar el mensaje: " + ex.getMessage());
                });
            }
        });
    }

    private void chooseAndSendImage() {
        if (sending || conversationId == null) {
            return;
        }
        String caption = input.getText() == null ? "" : input.getText().trim();
        if (caption.length() > PrivateChatRepository.MAX_BODY_LENGTH) {
            onStatus.accept("El mensaje supera el limite de " + PrivateChatRepository.MAX_BODY_LENGTH + " caracteres.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enviar imagen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imagenes (JPG, PNG, WebP)", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        String targetConversation = conversationId;
        setSending(true);
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage saved = privateChatRepository().sendImage(targetConversation, caption, file);
                Platform.runLater(() -> {
                    setSending(false);
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    input.clear();
                    if (upsertAscending(saved)) {
                        renderMessages();
                        scrollToBottom();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setSending(false);
                    onStatus.accept("No pudimos enviar tu imagen. Comprueba que sea una foto valida e intentalo de nuevo.");
                });
            }
        });
    }

    // ── Edicion / borrado individual ─────────────────────────────────────────────

    private void showEditDialog(PrivateChatDtos.PrivateMessage message) {
        if (!isOwnMutableMessage(message) || message.body() == null || message.body().isBlank()) {
            return;
        }
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Editar mensaje");
        dialog.setHeaderText("Actualiza el texto del mensaje.");

        TextArea editor = new TextArea(message.body());
        editor.setWrapText(true);
        editor.setPrefRowCount(4);
        editor.setMaxWidth(420);

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(editor);
        DialogStyler.apply(dialog);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        Runnable updateSaveState = () -> {
            String text = editor.getText() == null ? "" : editor.getText().trim();
            saveButton.setDisable(text.isEmpty()
                    || text.equals(message.body())
                    || text.length() > PrivateChatRepository.MAX_BODY_LENGTH);
        };
        editor.textProperty().addListener((obs, old, value) -> updateSaveState.run());
        updateSaveState.run();

        dialog.setResultConverter(type -> type == saveType ? editor.getText() : null);
        dialog.showAndWait()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .ifPresent(text -> editMessage(message, text));
    }

    private void editMessage(PrivateChatDtos.PrivateMessage message, String body) {
        if (conversationId == null) {
            return;
        }
        onStatus.accept("Guardando mensaje...");
        String targetConversation = conversationId;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage updated =
                        privateChatRepository().edit(targetConversation, message.id(), body);
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    upsertAscending(updated);
                    renderMessages();
                    onStatus.accept("Mensaje editado.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo editar el mensaje."));
            }
        });
    }

    private void confirmDeleteMessage(PrivateChatDtos.PrivateMessage message) {
        if (!isOwnMutableMessage(message)) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar mensaje");
        confirm.setHeaderText("¿Eliminar este mensaje?");
        confirm.setContentText("Se mostrara como mensaje eliminado.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyler.apply(confirm);
        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                deleteMessage(message);
            }
        });
    }

    private void deleteMessage(PrivateChatDtos.PrivateMessage message) {
        if (conversationId == null) {
            return;
        }
        onStatus.accept("Eliminando mensaje...");
        String targetConversation = conversationId;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessage updated = privateChatRepository().delete(targetConversation, message.id());
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    upsertAscending(updated);
                    renderMessages();
                    onStatus.accept("Mensaje eliminado.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo eliminar el mensaje."));
            }
        });
    }

    // ── Exportar / borrar ─────────────────────────────────────────────────────────

    private void exportChat() {
        if (conversationId == null) {
            return;
        }
        onStatus.accept("Exportando conversacion...");
        String targetConversation = conversationId;
        Thread.ofVirtual().start(() -> {
            try {
                PrivateChatDtos.PrivateMessageExport export = privateChatRepository().export(targetConversation);
                String text = buildExportText(export);
                Platform.runLater(() -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    Clipboard.getSystemClipboard().setContent(content);
                    onStatus.accept("Conversacion copiada al portapapeles ("
                            + export.totalMessages() + " mensajes).");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo exportar: " + ex.getMessage()));
            }
        });
    }

    private String buildExportText(PrivateChatDtos.PrivateMessageExport export) {
        StringBuilder builder = new StringBuilder("Conversacion privada - export\n\n");
        if (export.messages() != null) {
            for (PrivateChatDtos.PrivateMessage message : export.messages()) {
                List<PrivateChatDtos.PrivateAttachment> attachments = message.attachmentsOrEmpty();
                String body = message.body() != null ? message.body() : attachments.isEmpty() ? "(mensaje eliminado)" : "";
                builder.append('[').append(formatTime(message.createdAt())).append("] ")
                        .append(message.authorDisplayName()).append(": ")
                        .append(body);
                if (!attachments.isEmpty()) {
                    if (!body.isBlank()) {
                        builder.append(' ');
                    }
                    builder.append('[').append(attachments.size()).append(" imagen");
                    if (attachments.size() != 1) {
                        builder.append("es");
                    }
                    builder.append(']');
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void confirmClear() {
        if (conversationId == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Borrar chat para ti");
        confirm.setHeaderText("¿Borrar tu historial de esta conversacion?");
        confirm.setContentText("Se ocultara tu historial. El otro participante conserva el suyo. "
                + "Esta accion no se puede deshacer.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyler.apply(confirm);
        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                doClear();
            }
        });
    }

    private void doClear() {
        String targetConversation = conversationId;
        onStatus.accept("Borrando conversacion...");
        Thread.ofVirtual().start(() -> {
            try {
                privateChatRepository().clear(targetConversation);
                Platform.runLater(() -> {
                    if (!targetConversation.equals(conversationId)) {
                        return;
                    }
                    messages.clear();
                    hasMoreOlder = false;
                    nextBefore = null;
                    updateLoadOlderBtn();
                    renderMessages();
                    onStatus.accept("Conversacion borrada para ti.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("No se pudo borrar: " + ex.getMessage()));
            }
        });
    }

    // ── Render ─────────────────────────────────────────────────────────────────────

    private void renderMessages() {
        messagesBox.getChildren().clear();
        if (messages.isEmpty()) {
            messagesBox.getChildren().add(emptyState);
            return;
        }
        for (PrivateChatDtos.PrivateMessage message : messages) {
            messagesBox.getChildren().add(buildBubbleRow(message));
        }
    }

    private HBox buildBubbleRow(PrivateChatDtos.PrivateMessage message) {
        boolean mine = isMine(message);

        VBox bubble = new VBox(6);
        bubble.getStyleClass().add(mine ? "chat-bubble-mine" : "chat-bubble-other");
        bubble.setMaxWidth(440);
        if (isOwnMutableMessage(message)) {
            ContextMenu menu = buildMessageMenu(message);
            bubble.setOnContextMenuRequested(e -> {
                e.consume();
                menu.show(bubble, e.getScreenX(), e.getScreenY());
            });
            HBox actions = new HBox(buildMessageMenuButton(message));
            actions.setAlignment(Pos.CENTER_RIGHT);
            bubble.getChildren().add(actions);
        }

        if (!mine) {
            Label author = new Label(safe(message.authorDisplayName()));
            author.getStyleClass().add("chat-author");
            bubble.getChildren().add(author);
        }

        List<PrivateChatDtos.PrivateAttachment> attachments = message.attachmentsOrEmpty();
        for (PrivateChatDtos.PrivateAttachment attachment : attachments) {
            bubble.getChildren().add(buildAttachmentNode(attachment));
        }

        String bodyText = message.body() != null && !message.body().isBlank() ? message.body() : null;
        String visibleText = message.deleted() ? "(mensaje eliminado)" : bodyText;
        if (visibleText != null) {
            Label body = new Label(visibleText);
            body.setWrapText(true);
            body.getStyleClass().add(mine ? "chat-body-mine" : "chat-body");
            bubble.getChildren().add(body);
        }

        Label time = new Label(formatBubbleTime(message.createdAt()));
        time.getStyleClass().add(mine ? "chat-time-mine" : "chat-time");
        bubble.getChildren().add(time);

        HBox row = new HBox(bubble);
        row.setFillHeight(false);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private MenuButton buildMessageMenuButton(PrivateChatDtos.PrivateMessage message) {
        MenuButton button = new MenuButton("...");
        button.getStyleClass().add("action-button-secondary");
        button.getItems().addAll(buildMessageMenuItems(message));
        return button;
    }

    private ContextMenu buildMessageMenu(PrivateChatDtos.PrivateMessage message) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(buildMessageMenuItems(message));
        DialogStyler.apply(menu);
        return menu;
    }

    private List<MenuItem> buildMessageMenuItems(PrivateChatDtos.PrivateMessage message) {
        List<MenuItem> items = new ArrayList<>();
        if (message.body() != null && !message.body().isBlank()) {
            MenuItem editItem = new MenuItem("Editar");
            editItem.setOnAction(e -> showEditDialog(message));
            items.add(editItem);
        }
        MenuItem deleteItem = new MenuItem("Eliminar");
        deleteItem.setOnAction(e -> confirmDeleteMessage(message));
        items.add(deleteItem);
        return items;
    }

    private StackPane buildAttachmentNode(PrivateChatDtos.PrivateAttachment attachment) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("chat-attachment-frame");
        frame.setMinSize(240, 170);
        frame.setPrefSize(240, 170);
        frame.setMaxSize(240, 170);

        Label loading = new Label("Cargando imagen...");
        loading.getStyleClass().add("chat-attachment-placeholder");
        frame.getChildren().add(loading);

        String url = attachment.thumbnailUrl() != null && !attachment.thumbnailUrl().isBlank()
                ? attachment.thumbnailUrl()
                : attachment.url();
        if (url == null || url.isBlank()) {
            loading.setText("Imagen no disponible");
            return frame;
        }

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = context.getApiClient().fetchImage(url);
                Image image = new Image(new ByteArrayInputStream(bytes), 240, 170, true, true);
                Platform.runLater(() -> {
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(240);
                    imageView.setFitHeight(170);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    frame.getChildren().setAll(imageView);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> loading.setText("Imagen no disponible"));
            }
        });

        String originalUrl = attachment.url();
        if (originalUrl != null && !originalUrl.isBlank()) {
            frame.setCursor(Cursor.HAND);
            Tooltip.install(frame, new Tooltip("Ver imagen completa"));
            frame.setOnMouseClicked(e -> openAttachmentViewer(attachment));
        }
        return frame;
    }

    private void openAttachmentViewer(PrivateChatDtos.PrivateAttachment attachment) {
        String url = attachment.url();
        if (url == null || url.isBlank()) {
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.setTitle("Imagen de la conversacion");
        dialog.getDialogPane().getButtonTypes().addAll(SAVE_BUTTON_TYPE, ButtonType.CLOSE);

        StackPane content = new StackPane();
        content.setPrefSize(720, 520);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        content.getChildren().add(spinner);
        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(SAVE_BUTTON_TYPE);
        saveButton.setDisable(true);
        final byte[][] loaded = new byte[1][];
        saveButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            if (loaded[0] != null) {
                saveAttachmentToDisk(attachment, loaded[0]);
            }
        });

        final boolean[] closed = {false};
        dialog.setOnHidden(e -> closed[0] = true);

        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = context.getApiClient().fetchImage(url);
                Platform.runLater(() -> {
                    if (closed[0]) {
                        return;
                    }
                    Image image = new Image(new ByteArrayInputStream(bytes), 700, 480, true, true);
                    if (image.isError()) {
                        content.getChildren().setAll(viewerErrorLabel());
                        return;
                    }
                    ImageView view = new ImageView(image);
                    view.setPreserveRatio(true);
                    view.setSmooth(true);
                    view.setFitWidth(700);
                    view.setFitHeight(480);
                    content.getChildren().setAll(view);
                    loaded[0] = bytes;
                    saveButton.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!closed[0]) {
                        content.getChildren().setAll(viewerErrorLabel());
                    }
                });
            }
        });

        dialog.showAndWait();
    }

    private Label viewerErrorLabel() {
        Label label = new Label("No se pudo cargar la imagen");
        label.getStyleClass().add("chat-attachment-placeholder");
        return label;
    }

    private void saveAttachmentToDisk(PrivateChatDtos.PrivateAttachment attachment, byte[] bytes) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar imagen");
        String extension = extensionForContentType(attachment.contentType());
        chooser.setInitialFileName("recetas-dm-" + System.currentTimeMillis() + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen", "*" + extension));
        File target = getScene() != null
                ? chooser.showSaveDialog(getScene().getWindow())
                : chooser.showSaveDialog(null);
        if (target == null) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                Files.write(target.toPath(), bytes);
                Platform.runLater(() -> onStatus.accept("Imagen guardada en " + target.getName()));
            } catch (IOException ex) {
                Platform.runLater(() -> onStatus.accept("No pudimos guardar la imagen."));
            }
        });
    }

    private static String extensionForContentType(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        String normalized = contentType.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("png")) {
            return ".png";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private boolean isMine(PrivateChatDtos.PrivateMessage message) {
        return myUserId != null && myUserId.equals(message.authorUserId());
    }

    private boolean isOwnMutableMessage(PrivateChatDtos.PrivateMessage message) {
        return message != null && !message.deleted() && isMine(message);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void updateLoadOlderBtn() {
        loadOlderBtn.setVisible(hasMoreOlder);
        loadOlderBtn.setManaged(hasMoreOlder);
    }

    private void setSending(boolean isSending) {
        this.sending = isSending;
        sendBtn.setDisable(isSending);
        attachBtn.setDisable(isSending);
    }

    private void updateCharCounter(String value) {
        int length = value == null ? 0 : value.length();
        boolean nearLimit = length >= PrivateChatRepository.MAX_BODY_LENGTH - 160;
        charCounter.setText(length + "/" + PrivateChatRepository.MAX_BODY_LENGTH);
        charCounter.setVisible(nearLimit);
        charCounter.setManaged(nearLimit);
    }

    private PrivateChatRepository privateChatRepository() {
        return context.getPrivateChatRepository();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String formatTime(String iso) {
        ZonedDateTime local = parseLocal(iso);
        return local != null ? local.format(TIME_FORMAT) : safe(iso);
    }

    private static String formatBubbleTime(String iso) {
        ZonedDateTime local = parseLocal(iso);
        if (local == null) {
            return safe(iso);
        }
        String time = local.format(TIME_FORMAT);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate day = local.toLocalDate();
        if (day.isEqual(today)) {
            return time;
        }
        if (day.isEqual(today.minusDays(1))) {
            return "Ayer · " + time;
        }
        String datePart = day.getYear() == today.getYear()
                ? day.format(DAY_FORMAT)
                : day.format(DAY_YEAR_FORMAT);
        return datePart + " · " + time;
    }

    private static ZonedDateTime parseLocal(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault());
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/PrivateChatView.java
git commit -m "feat(desktop): PrivateChatView - panel de conversacion privada"
```

---

### Task 8: `ConversationsView` — bandeja de conversaciones

**Files:**
- Create: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/ConversationsView.java`

Mirrors `RecipeListView.java`'s shape: `ScrollPane` → `SplitPane` → list (left) + detail (right, here `PrivateChatView`). The list shows avatar-less rows (name, last message preview, unread badge if any).

- [ ] **Step 1: Write the file**

```java
package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gipsybuho.recetasfamiliares.api.dto.PrivateChatDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.Map;

/**
 * Bandeja de conversaciones privadas: lista a la izquierda, panel de mensajes
 * de la conversacion seleccionada a la derecha. Mismo patron que RecipeListView
 * + RecipeDetailView (ver addendum 2026-07-22 de la spec de chat privado).
 */
public class ConversationsView extends ScrollPane {

    private final AppContext context;
    private final Runnable onSync;
    private final SplitPane splitPane = new SplitPane();
    private final ListView<PrivateChatDtos.PrivateConversation> listView = new ListView<>();
    private final PrivateChatView detailView;
    private final Label statusLabel = new Label();
    private final Button refreshBtn = new Button("Actualizar");

    private Map<String, Integer> unreadByConversation = Map.of();

    public ConversationsView(AppContext context, Runnable onSync) {
        this.context = context;
        this.onSync = onSync;
        this.detailView = new PrivateChatView(context, this::setStatus);
        build();
    }

    private void build() {
        DesktopScroll.configurePage(this, splitPane);
        splitPane.getStyleClass().add("recipe-list-view");
        setContent(splitPane);

        Label header = new Label("Conversaciones");
        header.getStyleClass().add("view-header");

        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> refresh());

        listView.getStyleClass().add("recipe-list");
        listView.setPlaceholder(new Label("Sin conversaciones todavia."));
        listView.setCellFactory(lv -> new ConversationCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, conversation) -> {
            if (conversation != null) {
                openConversation(conversation);
            }
        });

        statusLabel.getStyleClass().add("status-label");

        VBox leftPanel = new VBox(10, header, refreshBtn, listView, statusLabel);
        leftPanel.setPadding(new Insets(16));
        VBox.setVgrow(listView, Priority.ALWAYS);
        leftPanel.setMinWidth(280);
        leftPanel.setMaxWidth(340);

        splitPane.getItems().addAll(leftPanel, detailView);
        splitPane.setDividerPositions(0.35);
    }

    public void refresh() {
        statusLabel.setText("Cargando...");
        Thread.ofVirtual().start(() -> {
            try {
                var conversations = context.getPrivateChatRepository().listConversations();
                Platform.runLater(() -> {
                    var selected = listView.getSelectionModel().getSelectedItem();
                    listView.getItems().setAll(conversations);
                    statusLabel.setText(conversations.length + " conversacion(es)");
                    if (selected != null) {
                        for (var conversation : conversations) {
                            if (conversation.conversationId().equals(selected.conversationId())) {
                                listView.getSelectionModel().select(conversation);
                                break;
                            }
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("No se pudieron cargar las conversaciones."));
            }
        });
    }

    /** Abre (creando si hace falta) la conversacion con otro miembro. Llamado desde Miembros. */
    public void openWith(String otherUserId) {
        statusLabel.setText("Abriendo conversacion...");
        Thread.ofVirtual().start(() -> {
            try {
                var conversation = context.getPrivateChatRepository().createOrGetConversation(otherUserId);
                Platform.runLater(() -> {
                    refresh();
                    openConversation(conversation);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("No se pudo abrir la conversacion: " + ex.getMessage()));
            }
        });
    }

    private void openConversation(PrivateChatDtos.PrivateConversation conversation) {
        detailView.open(conversation);
    }

    /** Actualiza los contadores de no-leidos mostrados en cada fila (badge de la sidebar por conversacion). */
    public void updateUnread(Map<String, Integer> unreadByConversation) {
        this.unreadByConversation = unreadByConversation;
        listView.refresh();
    }

    /** Se llama al salir de esta pantalla: libera la suscripcion a la conversacion abierta. */
    public void onHidden() {
        detailView.close();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private final class ConversationCell extends ListCell<PrivateChatDtos.PrivateConversation> {
        @Override
        protected void updateItem(PrivateChatDtos.PrivateConversation conversation, boolean empty) {
            super.updateItem(conversation, empty);
            if (empty || conversation == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(conversation.otherUserDisplayName() != null
                    ? conversation.otherUserDisplayName() : "Conversacion");
            title.getStyleClass().add("recipe-cell-title");
            Label preview = new Label(conversation.lastMessagePreview() != null
                    ? conversation.lastMessagePreview() : "");
            preview.getStyleClass().add("recipe-cell-meta");
            VBox textBox = new VBox(3, title, preview);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            HBox row = new HBox(8, textBox);
            Integer unread = unreadByConversation.get(conversation.conversationId());
            if (unread != null && unread > 0) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label badge = new Label(unread > 9 ? "9+" : String.valueOf(unread));
                badge.getStyleClass().add("profile-role-badge");
                row.getChildren().addAll(spacer, badge);
            }
            row.getStyleClass().add("recipe-cell");
            setGraphic(row);
            setText(null);
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/ConversationsView.java
git commit -m "feat(desktop): ConversationsView - bandeja de conversaciones privadas"
```

---

### Task 9: `FamilyMembersView` — boton "Mensaje" por fila

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java`

- [ ] **Step 1: Add the import and constructor param**

Add import (alongside the existing `java.util.List; java.util.Optional;`):
```java
import java.util.function.Consumer;
```

Change the field/constructor block:
```java
    private final Runnable onFamiliesChanged;
    private final Consumer<String> onMessageMember;

    public FamilyMembersView(AppContext context, Runnable onFamiliesChanged, Consumer<String> onMessageMember) {
        this.context = context;
        this.onFamiliesChanged = onFamiliesChanged;
        this.onMessageMember = onMessageMember;
        build();
        context.getChatRepository().setPresenceListener(online ->
                Platform.runLater(() -> applyPresence(online)));
        refresh();
    }
```

- [ ] **Step 2: Add the "Mensaje" column** (inside `buildTable()`, right after the `onlineCol` block, before `nameCol`'s declaration)

```java
        TableColumn<MemberRow, MemberRow> messageCol = new TableColumn<>("");
        messageCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        messageCol.setCellFactory(col -> new TableCell<>() {
            private final Button messageBtn = new Button("✉");

            {
                messageBtn.getStyleClass().add("action-button-secondary");
                Tooltip.install(messageBtn, new Tooltip("Enviar mensaje privado"));
                messageBtn.setOnAction(e -> {
                    MemberRow row = getTableRow().getItem();
                    if (row != null) {
                        onMessageMember.accept(row.getUserId());
                    }
                });
            }

            @Override
            protected void updateItem(MemberRow row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(empty || row == null || row.isSelf() ? null : messageBtn);
            }
        });
        messageCol.setSortable(false);
        messageCol.setResizable(false);
        messageCol.setMinWidth(40);
        messageCol.setMaxWidth(40);
```

Add `tv.getColumns().add(messageCol);` right after `tv.getColumns().add(onlineCol);` (before `tv.getColumns().add(nameCol);`).

- [ ] **Step 3: Compile**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: compile error in `MainWindow.java` (constructor call site now has the wrong arity) — expected, fixed in Task 10.

- [ ] **Step 4: Commit** (deferred to end of Task 10)

---

### Task 10: `MainWindow` — abrir Miembros a todos los roles, nuevo item de sidebar, badge

**Files:**
- Modify: `desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java`

- [ ] **Step 1: New field** (alongside `private FamilyMembersView familyMembersView;`)

```java
    private ConversationsView conversationsView;
```

Add `btnConversations` to the button field declaration (the line `private Button btnDashboard, btnRecipes, ..., btnSettings, btnMembers;`):
```java
    private Button btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes, btnChat,
            btnConversations, btnSettings, btnMembers;
```

- [ ] **Step 2: Construct `ConversationsView` and open Miembros to all roles** (in `showMain()`, replace the two relevant blocks)

Replace:
```java
        chatView.startRealtime();
        profileView = new ProfileView(context, stage, this::refreshUserCard, this::setStatus,
                this::showLogin);
```
with:
```java
        chatView.startRealtime();
        conversationsView = new ConversationsView(context, this::triggerSync);
        context.getChatRepository().setInboxListener(this::updatePrivateChatBadge);
        profileView = new ProfileView(context, stage, this::refreshUserCard, this::setStatus,
                this::showLogin);
```

Replace:
```java
        if (context.getSession().isAdmin()) {
            familyMembersView = new FamilyMembersView(context, this::reloadFamilyChoices);
        }
```
with:
```java
        familyMembersView = new FamilyMembersView(context, this::reloadFamilyChoices, this::openConversationWith);
```

- [ ] **Step 3: Add the sidebar button and open Miembros' button to all roles** (in `buildSidebar()`)

Replace:
```java
        btnChat      = sidebarButton("💬  Chat familiar", "chat");
        btnSettings  = sidebarButton("⚙  Ajustes", "settings");
```
with:
```java
        btnChat      = sidebarButton("💬  Chat familiar", "chat");
        btnConversations = sidebarButton("🔒  Chat privado", "conversations");
        btnSettings  = sidebarButton("⚙  Ajustes", "settings");
```

Replace:
```java
        VBox navigation = new VBox();
        navigation.getStyleClass().add("sidebar-navigation");
        navigation.getChildren().addAll(
                btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes, btnChat,
                btnSettings);

        // ── Admin-only buttons ────────────────────────────────────────────────
        if (context.getSession().isAdmin()) {
            Separator adminSep = new Separator();
            VBox.setMargin(adminSep, new Insets(4, 16, 4, 16));

            btnMembers  = sidebarButton("👨‍👩‍👧  Miembros", "members");
            navigation.getChildren().addAll(adminSep, btnMembers);
        }
```
with:
```java
        VBox navigation = new VBox();
        navigation.getStyleClass().add("sidebar-navigation");
        navigation.getChildren().addAll(
                btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping, btnNotes, btnChat,
                btnConversations, btnSettings);

        Separator membersSep = new Separator();
        VBox.setMargin(membersSep, new Insets(4, 16, 4, 16));
        btnMembers = sidebarButton("👨‍👩‍👧  Miembros", "members");
        navigation.getChildren().addAll(membersSep, btnMembers);
```

- [ ] **Step 4: Navigation, badge and open-Members-to-all-roles in `navigateTo()`** (replace the two relevant blocks)

Replace:
```java
            case "chat" -> {
                setCenterWithFade(chatView);
                chatView.onShown();
            }
            case "members" -> {
                if (context.getSession().isAdmin() && familyMembersView != null) {
                    setCenterWithFade(familyMembersView);
                    familyMembersView.refresh();
                }
            }
```
with:
```java
            case "chat" -> {
                setCenterWithFade(chatView);
                chatView.onShown();
            }
            case "conversations" -> {
                setCenterWithFade(conversationsView);
                conversationsView.refresh();
            }
            case "members" -> {
                setCenterWithFade(familyMembersView);
                familyMembersView.refresh();
            }
```

Also add, right after the existing `if (chatView != null && !"chat".equals(view)) { chatView.onHidden(); }` block in `navigateTo()`:
```java
        if (conversationsView != null && !"conversations".equals(view)) {
            conversationsView.onHidden();
        }
```

- [ ] **Step 5: Badge method and active-button wiring**

Add the new method near `updateChatBadge`:
```java
    /** Badge de no-leidos del chat privado en la sidebar (suma de todas las conversaciones). */
    private void updatePrivateChatBadge(java.util.Map<String, Integer> unreadByConversation) {
        if (conversationsView != null) {
            conversationsView.updateUnread(unreadByConversation);
        }
        if (btnConversations == null) {
            return;
        }
        int total = unreadByConversation.values().stream().mapToInt(Integer::intValue).sum();
        String base = "🔒  Chat privado";
        btnConversations.setText(total > 0 ? base + "  (" + (total > 9 ? "9+" : total) + ")" : base);
    }
```

Add `btnConversations` to `updateActiveSidebarButton`'s array and switch:
```java
        Button[] navButtons = {btnDashboard, btnRecipes, btnStock, btnMenu, btnShopping,
                btnNotes, btnChat, btnConversations, btnSettings, btnMembers};
        ...
        Button active = switch (view) {
            case "dashboard" -> btnDashboard;
            case "recipes"   -> btnRecipes;
            case "stock"     -> btnStock;
            case "menu"      -> btnMenu;
            case "shopping"  -> btnShopping;
            case "notes"     -> btnNotes;
            case "chat"      -> btnChat;
            case "conversations" -> btnConversations;
            case "settings"  -> btnSettings;
            case "members"   -> btnMembers;
            default          -> null;
        };
```

- [ ] **Step 6: `openConversationWith` callback**

Add near `showSearchResults`/`onSearchResultClicked`:
```java
    private void openConversationWith(String otherUserId) {
        navigateTo("conversations");
        conversationsView.openWith(otherUserId);
    }
```

- [ ] **Step 7: Compile and run the full desktop suite**

Run: `mvn -f desktop/pom.xml test`
Expected: `BUILD SUCCESS`, all tests pass (same count as Task 5 plus no new test files here — this task is UI wiring, covered by the manual check in Task 12, consistent with how the rest of `MainWindow`'s navigation wiring has no unit tests today).

- [ ] **Step 8: Commit** (Tasks 9 + 10 together)

```bash
git add desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/FamilyMembersView.java desktop/src/main/java/org/gipsybuho/recetasfamiliares/ui/MainWindow.java
git commit -m "feat(desktop): abre Miembros a todos los roles, boton Mensaje y navegacion a Conversaciones"
```

---

### Task 11: VibeSec sobre el diff completo del cliente

**Files:** ninguno (revision, no cambios de codigo salvo que aparezcan hallazgos)

- [ ] **Step 1: Invocar `/VibeSec`** sobre el diff completo de este plan (Tasks 1-10), con foco en:
  - Que `FamilyMembersView` abierta a todos los roles no exponga ninguna accion de gestion (Add/Edit/ChangeRole/Remove/CreateFamily) a no-admins — ya gateado por `toolbar.setVisible(isAdmin)` existente, verificar que sigue siendo asi tras el diff.
  - Que el boton "Mensaje" nunca aparezca en la propia fila del usuario (`row.isSelf()`).
  - Que ningun dato de otra conversacion se filtre entre conversaciones (el guard `!targetConversation.equals(conversationId)` en cada callback async de `PrivateChatView` evita que una respuesta tardia de una conversacion ya cerrada se pinte en la que esta abierta ahora).
  - Que las imagenes de chat privado se descarguen con el mismo `fetchImage` autenticado que el chat familiar (sin nuevas rutas sin JWT).
  - Que el backend ya cubre 404-no-403 en conversaciones ajenas (fuera del alcance de este plan, solo confirmar que el cliente no depende de logica de autorizacion propia — no deberia, todo el filtrado real vive en backend).
- [ ] **Step 2: Corregir cualquier hallazgo Critical/Important antes de continuar.** Si no hay hallazgos, documentarlo explicitamente (no afirmar "sin hallazgos" salvo que la skill se haya ejecutado de verdad en esta sesion).

---

### Task 12: Validacion final

**Files:** ninguno

- [ ] **Step 1: Compilacion completa**

Run: `mvn -f desktop/pom.xml -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: Suite completa de tests**

Run: `mvn -f desktop/pom.xml test`
Expected: `BUILD SUCCESS`, 0 fallos nuevos (compara el conteo total contra el de antes de este plan — hoy 33 tests, este plan añade 11 en `PrivateChatRepositoryHttpTest` + 2 en `ChatSocketFrameParsingTest` = 46 esperados).

- [ ] **Step 3: Prueba manual — documentar como pendiente, no simularla**

Bloqueada para el agente en este entorno (interaccion de clics/dos sesiones reales no disponible, mismo motivo documentado en sprints anteriores de presencia/chat). Pendiente de que el usuario, con dos cuentas de la misma familia:
- Abra "Chat privado" desde el sidebar y desde el boton "Mensaje" en Miembros.
- Confirme que un miembro no-admin ahora ve "Miembros" y puede iniciar conversacion.
- Envie un mensaje y confirme que llega en vivo a la otra sesion sin recargar.
- Confirme que el badge de no-leidos sube cuando la conversacion no esta abierta y baja a 0 al abrirla.
- Confirme que un tercer miembro de la familia (no participante) no ve la conversacion en su propia bandeja.

- [ ] **Step 4: Actualizar `CONTINUAR.md`** con el cierre de este sprint (agente lider, skills usadas, seguridad ejecutada, archivos, validacion, riesgo residual de la prueba manual pendiente) y pedir autorizacion antes de push, siguiendo el mismo patron que los sprints anteriores documentados en ese archivo.
