package org.gipsybuho.recetasfamiliares.families;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class FamilyMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listMembersReturnsSelfAsOwner() throws Exception {
        RegisteredUser user = register("members-self@example.com", "Familia Miembros");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("members-self@example.com"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void listMembersReturnsForbiddenForNonMember() throws Exception {
        RegisteredUser ownerA = register("members-owner-a@example.com", "Familia A");
        RegisteredUser userB  = register("members-user-b@example.com", "Familia B");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", ownerA.familyId())
                        .header("Authorization", "Bearer " + userB.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMembersRequiresAuthentication() throws Exception {
        RegisteredUser user = register("members-noauth@example.com", "Familia NoAuth");

        mockMvc.perform(get("/api/v1/families/{familyId}/members", user.familyId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOwnerRoleReturnsBadRequest() throws Exception {
        RegisteredUser user = register("members-role-owner@example.com", "Familia RoleOwner");

        mockMvc.perform(put("/api/v1/families/{familyId}/members/{userId}/role",
                        user.familyId(), user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeSelfReturnsBadRequest() throws Exception {
        RegisteredUser user = register("members-remove-self@example.com", "Familia RemoveSelf");

        mockMvc.perform(delete("/api/v1/families/{familyId}/members/{userId}",
                        user.familyId(), user.userId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record RegisteredUser(String accessToken, String familyId, String userId) {}

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Test User",
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
}
