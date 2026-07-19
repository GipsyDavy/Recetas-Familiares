package org.gipsybuho.recetasfamiliares.presence;

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
class PresenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsEmptySnapshotForFamilyMemberWithNoActiveConnections() throws Exception {
        RegisteredUser owner = register(uniqueEmail("presence-owner"), "Familia Presencia");

        mockMvc.perform(get("/api/v1/families/{familyId}/presence", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineUserIds").isArray())
                .andExpect(jsonPath("$.onlineUserIds.length()").value(0));
    }

    @Test
    void blocksPresenceAccessAcrossFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("presence-private-owner"), "Familia Presencia Privada");
        RegisteredUser other = register(uniqueEmail("presence-private-other"), "Familia Presencia Otra");

        mockMvc.perform(get("/api/v1/families/{familyId}/presence", owner.familyId())
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden());
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Presence User",
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

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}
}
