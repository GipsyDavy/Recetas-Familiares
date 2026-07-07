package org.gipsybuho.recetasfamiliares.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sendsListsWithEmojiAndIsIdempotentByClientId() throws Exception {
        RegisteredUser user = register("chat-basic@example.com", "Familia Chat");
        String clientId = "11111111-1111-4111-8111-111111111111";
        String emojiBody = "Hola familia 👩‍🍳 🍲";

        send(user, clientId, emojiBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.familyId").value(user.familyId()))
                .andExpect(jsonPath("$.authorUserId").value(user.userId()))
                .andExpect(jsonPath("$.authorDisplayName").value("Chat User"))
                .andExpect(jsonPath("$.body").value(emojiBody))
                .andExpect(jsonPath("$.deleted").value(false));

        // Reenvio idempotente con el mismo id: no duplica.
        send(user, clientId, emojiBody).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(clientId))
                .andExpect(jsonPath("$.items[0].body").value(emojiBody))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void blocksChatAccessAcrossFamilies() throws Exception {
        RegisteredUser first = register("chat-fam-one@example.com", "Familia Uno");
        RegisteredUser second = register("chat-fam-two@example.com", "Familia Dos");
        send(second, null, "Mensaje privado").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/families/{familyId}/chat/messages", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "No deberia entrar"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void clearHidesHistoryOnlyForClearingUser() throws Exception {
        RegisteredUser owner = register("chat-clear-owner@example.com", "Familia Clear");
        RegisteredUser guest = registerAndJoin("chat-clear-guest@example.com", owner);

        send(owner, null, "Primer recuerdo").andExpect(status().isCreated());
        send(owner, null, "Segundo recuerdo").andExpect(status().isCreated());

        // El invitado ve ambos.
        mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        // El invitado limpia SU vista.
        mockMvc.perform(post("/api/v1/families/{familyId}/chat/clear", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        // El propietario sigue viendo su historial completo.
        mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        // Mensajes nuevos tras la limpieza si son visibles para el invitado.
        send(guest, null, "Empiezo de nuevo").andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Empiezo de nuevo"));
    }

    @Test
    void exportReflectsUserViewAscending() throws Exception {
        RegisteredUser user = register("chat-export@example.com", "Familia Export");
        send(user, null, "Uno").andExpect(status().isCreated());
        send(user, null, "Dos").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/chat/export", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyId").value(user.familyId()))
                .andExpect(jsonPath("$.totalMessages").value(2))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].body").value("Uno"))
                .andExpect(jsonPath("$.messages[1].body").value("Dos"));

        mockMvc.perform(post("/api/v1/families/{familyId}/chat/clear", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/chat/export", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMessages").value(0));
    }

    @Test
    void paginatesHistoryByCursorWithoutLosingMessages() throws Exception {
        RegisteredUser user = register("chat-page@example.com", "Familia Page");
        Set<String> sent = new HashSet<>();
        sent.add(read(send(user, null, "m1").andReturn(), "id"));
        sent.add(read(send(user, null, "m2").andReturn(), "id"));
        sent.add(read(send(user, null, "m3").andReturn(), "id"));

        MvcResult page1 = mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        String nextBefore = read(page1, "nextBefore");

        MvcResult page2 = mockMvc.perform(get("/api/v1/families/{familyId}/chat/messages", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .param("limit", "2")
                        .param("before", nextBefore))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn();

        Set<String> paged = new HashSet<>();
        collectIds(page1, paged);
        collectIds(page2, paged);
        Assertions.assertEquals(sent, paged, "las paginas deben cubrir exactamente los mensajes enviados");
    }

    @Test
    void rejectsEmptyBody() throws Exception {
        RegisteredUser user = register("chat-empty@example.com", "Familia Empty");

        mockMvc.perform(post("/api/v1/families/{familyId}/chat/messages", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void rateLimitsBurstSends() throws Exception {
        RegisteredUser user = register("chat-rate@example.com", "Familia Rate");
        for (int i = 0; i < 10; i++) {
            send(user, null, "msg " + i).andExpect(status().isCreated());
        }
        send(user, null, "uno de mas").andExpect(status().isTooManyRequests());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record RegisteredUser(String accessToken, String familyId, String userId) {
    }

    private org.springframework.test.web.servlet.ResultActions send(RegisteredUser user, String id, String body)
            throws Exception {
        String payload = id == null
                ? objectMapper.writeValueAsString(new SendChatMessageRequest(null, body))
                : objectMapper.writeValueAsString(new SendChatMessageRequest(id, body));
        return mockMvc.perform(post("/api/v1/families/{familyId}/chat/messages", user.familyId())
                .header("Authorization", "Bearer " + user.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Chat User",
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
                response.get("user").get("id").asText()
        );
    }

    /**
     * Registra un usuario y lo une a la familia del owner (via alta de miembro),
     * devolviendo credenciales dentro de esa familia.
     */
    private RegisteredUser registerAndJoin(String email, RegisteredUser owner) throws Exception {
        register(email, "Familia Temporal " + email);
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "very-secure-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new RegisteredUser(
                response.get("accessToken").asText(),
                owner.familyId(),
                response.get("user").get("id").asText()
        );
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode value = response.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void collectIds(MvcResult result, Set<String> target) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (JsonNode item : response.get("items")) {
            target.add(item.get("id").asText());
        }
    }
}
