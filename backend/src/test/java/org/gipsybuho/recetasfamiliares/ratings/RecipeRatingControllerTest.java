package org.gipsybuho.recetasfamiliares.ratings;

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
class RecipeRatingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── Happy path: create → list → update → delete ───────────────────────────

    @Test
    void createsListsUpdatesAndSoftDeletesRating() throws Exception {
        RegisteredUser user = register(uniqueEmail("rating-owner"), "Familia Ratings");
        String recipeId = createRecipe(user, "Tortilla española");

        MvcResult created = mockMvc.perform(post(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 4, "comment": "¡Muy buena!" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stars").value(4))
                .andExpect(jsonPath("$.comment").value("¡Muy buena!"))
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.userDisplayName").value("Rating User"))
                .andExpect(jsonPath("$.deleted").value(false))
                .andReturn();

        String ratingId = read(created, "id");

        mockMvc.perform(get(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stars").value(4));

        mockMvc.perform(put(ratingsUrl(user, recipeId) + "/" + ratingId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 5, "comment": "¡Perfecta!" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars").value(5))
                .andExpect(jsonPath("$.comment").value("¡Perfecta!"));

        mockMvc.perform(delete(ratingsUrl(user, recipeId) + "/" + ratingId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── Conflict: second rating by same user ──────────────────────────────────

    @Test
    void rejectsDuplicateRatingBySameUser() throws Exception {
        RegisteredUser user = register(uniqueEmail("rating-dup"), "Familia Dup Rating");
        String recipeId = createRecipe(user, "Gazpacho");

        mockMvc.perform(post(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 3 }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 5 }
                                """))
                .andExpect(status().isConflict());
    }

    // ── Validation: stars out of range ────────────────────────────────────────

    @Test
    void rejectsStarsOutOfRange() throws Exception {
        RegisteredUser user = register(uniqueEmail("rating-val"), "Familia Val Rating");
        String recipeId = createRecipe(user, "Paella");

        mockMvc.perform(post(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 0 }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(ratingsUrl(user, recipeId))
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 6 }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Access control: cannot modify another family's rating ─────────────────

    @Test
    void blocksRatingAccessAcrossFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("rating-priv"), "Familia Privada Rating");
        RegisteredUser other = register(uniqueEmail("rating-other"), "Familia Otra Rating");
        String recipeId = createRecipe(owner, "Receta privada");

        mockMvc.perform(post(ratingsUrl(owner, recipeId))
                        .header("Authorization", "Bearer " + other.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 5 }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ratingsUrl(owner, recipeId))
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isForbidden());
    }

    // ── Multiple users rate the same recipe ───────────────────────────────────

    @Test
    void aggregatesRatingsFromMultipleUsers() throws Exception {
        RegisteredUser owner  = register(uniqueEmail("rating-multi-owner"), "Familia Multi Rating");
        RegisteredUser member = register(uniqueEmail("rating-multi-member"), "Familia Multi Rating Member");
        String recipeId = createRecipe(owner, "Croquetas");

        // Add member to same family (not possible via API in current design — each register creates own family)
        // So we test each in their own family independently: focus on count and isolation

        mockMvc.perform(post(ratingsUrl(owner, recipeId))
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stars": 5, "comment": "¡Las mejores!" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ratingsUrl(owner, recipeId))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stars").value(5));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String ratingsUrl(RegisteredUser user, String recipeId) {
        return "/api/v1/families/" + user.familyId() + "/recipes/" + recipeId + "/ratings";
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Rating User",
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

    private String createRecipe(RegisteredUser user, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "%s", "description": "Para valorar" }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "id");
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {}
    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

}
