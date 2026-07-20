package org.gipsybuho.recetasfamiliares.dm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PrivateChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PrivateMessageRepository messageRepository;

    @Test
    void createsConversationBetweenTwoFamilyMembers() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-create-owner"), "Familia DM Create");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-create-guest"));

        MvcResult result = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.otherUserId").value(guest.userId()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String conversationId = body.get("conversationId").asText();

        // Idempotente: pedirla de nuevo devuelve el mismo id, sin importar quien la pide.
        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), owner.userId())
                        .header("Authorization", "Bearer " + guest.accessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId));
    }

    @Test
    void rejectsConversationWithNonFamilyMember() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-outsider-owner"), "Familia DM Outsider");
        RegisteredUser outsider = register(uniqueEmail("dm-outsider-other"), "Familia DM Outsider Otra");

        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), outsider.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsConversationWithSelf() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-self-owner"), "Familia DM Self");

        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), owner.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listConversationsReturnsOnlyConversationsOfRequester() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-list-owner"), "Familia DM List");
        RegisteredUser guestA = invite(owner, uniqueEmail("dm-list-guest-a"));
        RegisteredUser guestB = invite(owner, uniqueEmail("dm-list-guest-b"));

        mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guestA.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        // guestB no tiene conversacion con nadie: su bandeja debe estar vacia.
        mockMvc.perform(get("/api/v1/families/{familyId}/conversations", owner.familyId())
                        .header("Authorization", "Bearer " + guestB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].otherUserId").value(guestA.userId()));
    }

    @Test
    void sendsTextMessageAndListsHistory() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-send-owner"), "Familia DM Send");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-send-guest"));
        String conversationId = createConversation(owner, guest.userId());

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "hola guest"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("hola guest"))
                .andExpect(jsonPath("$.authorUserId").value(owner.userId()));

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("hola guest"));
    }

    @Test
    void blocksMessageAccessForNonParticipant() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-block-owner"), "Familia DM Block");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-block-guest"));
        RegisteredUser outsider = invite(owner, uniqueEmail("dm-block-outsider"));
        String conversationId = createConversation(owner, guest.userId());

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "no deberia poder"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendingTextMessageIsIdempotentByClientId() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-idem-owner"), "Familia DM Idem");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-idem-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String clientId = java.util.UUID.randomUUID().toString();

        String payload = """
                {"id": "%s", "body": "reintento"}
                """.formatted(clientId);

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void sendsImageMessageAndServesAttachmentOnlyToParticipants() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-image-owner"), "Familia DM Image");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-image-guest"));
        RegisteredUser outsider = invite(owner, uniqueEmail("dm-image-outsider"));
        String conversationId = createConversation(owner, guest.userId());

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", validJpeg());

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/families/{familyId}/conversations/{conversationId}/messages/images",
                                owner.familyId(), conversationId)
                        .file(file)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String attachmentUrl = body.get("attachments").get(0).get("url").asText();
        String path = attachmentUrl.substring(attachmentUrl.indexOf("/uploads/"));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateLimitsBurstSends() throws Exception {
        // ChatSendRateLimiter es el mismo bean que usa el chat familiar (compartido,
        // por userId — ver Global Constraints), ya probado en ChatControllerTest;
        // este test solo verifica el cableado en PrivateChatService: cuando el
        // limiter deniega, el endpoint responde 429 en vez de crear el mensaje.
        RegisteredUser owner = register(uniqueEmail("dm-ratelimit-owner"), "Familia DM RateLimit");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-ratelimit-guest"));
        String conversationId = createConversation(owner, guest.userId());

        int tooMany = 11; // limite por defecto: app.security.rate-limit.chat.max-messages=10
        int lastStatus = 0;
        for (int i = 0; i < tooMany; i++) {
            lastStatus = mockMvc.perform(post(
                            "/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                            owner.familyId(), conversationId)
                            .header("Authorization", "Bearer " + owner.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"body": "burst %d"}
                                    """.formatted(i)))
                    .andReturn().getResponse().getStatus();
        }

        org.junit.jupiter.api.Assertions.assertEquals(429, lastStatus);
    }

    @Test
    void editsOwnMessageWithinWindow() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-edit-owner"), "Familia DM Edit");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-edit-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "original");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "editado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("editado"));
    }

    @Test
    void rejectsEditAfterFifteenMinuteWindow() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-edit-expired-owner"), "Familia DM Edit Expired");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-edit-expired-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "Mensaje antiguo");

        PrivateMessageEntity message = messageRepository.findById(messageId).orElseThrow();
        ReflectionTestUtils.setField(message, "createdAt", Instant.now().minus(Duration.ofMinutes(16)));
        messageRepository.saveAndFlush(message);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Tarde"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void blocksEditingAnotherParticipantsMessage() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-edit-block-owner"), "Familia DM Edit Block");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-edit-block-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "de owner");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + guest.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "intento ajeno"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void softDeletesOwnMessage() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-delete-owner"), "Familia DM Delete");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-delete-guest"));
        String conversationId = createConversation(owner, guest.userId());
        String messageId = sendText(owner, conversationId, "a borrar");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages/{messageId}",
                        owner.familyId(), conversationId, messageId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.body").doesNotExist());
    }

    @Test
    void clearHidesHistoryOnlyForClearingUser() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-clear-owner"), "Familia DM Clear");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-clear-guest"));
        String conversationId = createConversation(owner, guest.userId());
        sendText(owner, conversationId, "antes de limpiar");

        mockMvc.perform(post("/api/v1/families/{familyId}/conversations/{conversationId}/clear",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void exportsVisibleMessagesAscending() throws Exception {
        RegisteredUser owner = register(uniqueEmail("dm-export-owner"), "Familia DM Export");
        RegisteredUser guest = invite(owner, uniqueEmail("dm-export-guest"));
        String conversationId = createConversation(owner, guest.userId());
        sendText(owner, conversationId, "primero");
        sendText(guest, conversationId, "segundo");

        mockMvc.perform(get("/api/v1/families/{familyId}/conversations/{conversationId}/export",
                        owner.familyId(), conversationId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMessages").value(2))
                .andExpect(jsonPath("$.messages[0].body").value("primero"))
                .andExpect(jsonPath("$.messages[1].body").value("segundo"));
    }

    private String sendText(RegisteredUser sender, String conversationId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/{conversationId}/messages",
                        sender.familyId(), conversationId)
                        .header("Authorization", "Bearer " + sender.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "%s"}
                                """.formatted(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get("id").asText();
    }

    private String createConversation(RegisteredUser owner, String otherUserId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), otherUserId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.get("conversationId").asText();
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "DM User",
                                  "password": "very-secure-password",
                                  "familyName": "%s"
                                }
                                """.formatted(email, familyName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new RegisteredUser(
                response.get("accessToken").asText(),
                response.get("family").get("id").asText(),
                response.get("user").get("id").asText());
    }

    /** Registra un segundo usuario y lo invita a la familia de {@code owner}. */
    private RegisteredUser invite(RegisteredUser owner, String guestEmail) throws Exception {
        RegisteredUser guest = register(guestEmail, "Familia DM Guest " + guestEmail);
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());
        return guest;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    /** Mismo patron que ChatControllerTest.validJpeg(): imagen real decodificable,
     * no bytes con solo cabecera magica (FileStorageService.storeWithThumbnail
     * decodifica de verdad para generar el thumbnail y width/height). */
    private byte[] validJpeg() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(image, "jpg", out)) {
            throw new IllegalStateException("No JPEG writer available");
        }
        return out.toByteArray();
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}
}
