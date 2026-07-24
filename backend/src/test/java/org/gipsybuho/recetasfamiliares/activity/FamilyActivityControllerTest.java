package org.gipsybuho.recetasfamiliares.activity;

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
class FamilyActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allSectionsFalseWhenNoActivityYet() throws Exception {
        RegisteredUser user = register(uniqueEmail("activity-empty"), "Familia Activity Empty");

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe").value(false))
                .andExpect(jsonPath("$.note").value(false))
                .andExpect(jsonPath("$.stock").value(false));
    }

    @Test
    void creatingRecipeMarksItUnseenForOtherMemberButNotForAuthor() throws Exception {
        RegisteredUser owner = register(uniqueEmail("activity-owner"), "Familia Activity");
        String guestEmail = uniqueEmail("activity-guest");
        RegisteredUser guest = register(guestEmail, "Familia Activity Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Tarta", "servings": 4, "prepMinutes": 10, "cookMinutes": 20, "difficulty": "EASY"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe").value(false));

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe").value(true));
    }

    @Test
    void markingSeenClearsTheFlag() throws Exception {
        RegisteredUser owner = register(uniqueEmail("activity-seen-owner"), "Familia Activity Seen");
        String guestEmail = uniqueEmail("activity-seen-guest");
        RegisteredUser guest = register(guestEmail, "Familia Activity Seen Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/families/{familyId}/notes", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Nota", "body": "cuerpo", "pinned": false}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/families/{familyId}/activity/NOTE/seen", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(false));
    }

    @Test
    void activityRequiresFamilyMembership() throws Exception {
        RegisteredUser owner = register(uniqueEmail("activity-forbidden-owner"), "Familia Forbidden");
        RegisteredUser outsider = register(uniqueEmail("activity-forbidden-outsider"), "Familia Ajena");

        mockMvc.perform(get("/api/v1/families/{familyId}/activity", owner.familyId())
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

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

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
