package org.gipsybuho.recetasfamiliares.dm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PrivateChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
