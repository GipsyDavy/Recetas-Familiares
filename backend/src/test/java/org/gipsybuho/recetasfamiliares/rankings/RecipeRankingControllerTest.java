package org.gipsybuho.recetasfamiliares.rankings;

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
class RecipeRankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ranksFamilyUsersByCreatedRecipesAndReceivedRatings() throws Exception {
        String ownerEmail = uniqueEmail("ranking-owner");
        String adminEmail = uniqueEmail("ranking-admin");
        RegisteredUser owner = register(ownerEmail, "Familia Ranking");
        RegisteredUser admin = register(adminEmail, "Familia Ranking Admin");
        invite(owner, adminEmail, "ADMIN");

        String ownerRecipeId = createRecipe(owner, owner.familyId(), "Receta owner");
        String adminRecipeId = createRecipe(admin, owner.familyId(), "Receta admin");

        rate(owner, owner.familyId(), ownerRecipeId, 5);
        rate(admin, owner.familyId(), ownerRecipeId, 4);
        rate(owner, owner.familyId(), adminRecipeId, 5);

        mockMvc.perform(get("/api/v1/families/{familyId}/recipe-rankings/users", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].userId").value(admin.userId()))
                .andExpect(jsonPath("$[0].displayName").value("Ranking User"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].recipesCreated").value(1))
                .andExpect(jsonPath("$[0].ratingsReceived").value(1))
                .andExpect(jsonPath("$[0].averageStars").value(5.0))
                .andExpect(jsonPath("$[0].score").value(6))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].userId").value(owner.userId()))
                .andExpect(jsonPath("$[1].ratingsReceived").value(1))
                .andExpect(jsonPath("$[1].averageStars").value(4.0))
                .andExpect(jsonPath("$[1].score").value(5));
    }

    @Test
    void blocksRankingAccessAcrossFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("ranking-private-owner"), "Familia Ranking Privada");
        RegisteredUser other = register(uniqueEmail("ranking-private-other"), "Familia Ranking Otra");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipe-rankings/users", owner.familyId())
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden());
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Ranking User",
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

    private void invite(RegisteredUser owner, String email, String role) throws Exception {
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "%s"}
                                """.formatted(email, role)))
                .andExpect(status().isCreated());
    }

    private String createRecipe(RegisteredUser user, String familyId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/families/{familyId}/recipes", familyId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "%s", "description": "Para ranking" }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get("id").asText();
    }

    private void rate(RegisteredUser user, String familyId, String recipeId, int stars) throws Exception {
        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/ratings", familyId, recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": %d }
                                """.formatted(stars)))
                .andExpect(status().isCreated());
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}
}
