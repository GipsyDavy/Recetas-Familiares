package org.gipsybuho.recetasfamiliares.families;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class FamilyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsOnlyFamiliesOwnedByAuthenticatedUser() throws Exception {
        String firstAccessToken = registerAndReadAccessToken(
                "familia-uno@example.com",
                "Familia Uno"
        );
        String secondAccessToken = registerAndReadAccessToken(
                "familia-dos@example.com",
                "Familia Dos"
        );

        mockMvc.perform(get("/api/v1/families")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Familia Uno"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));

        mockMvc.perform(get("/api/v1/families")
                        .header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Familia Dos"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    /** COD-4: lastActivityAt debe reflejar actividad de cualquier entidad, no solo recetas. */
    @Test
    void statsLastActivityCoversNonRecipeEntities() throws Exception {
        RegisteredUser user = register("familia-stats@example.com", "Familia Stats");

        // Sin actividad: lastActivityAt null
        mockMvc.perform(get("/api/v1/families/{familyId}/stats", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipes").value(0))
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.nullValue()));

        // Una nota (sin recetas) ya cuenta como actividad
        mockMvc.perform(post("/api/v1/families/{familyId}/notes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Nota", "body": "Actividad familiar", "pinned": false}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/stats", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void statsLastActivityCoversStockMenuAndShoppingWithoutRecipes() throws Exception {
        RegisteredUser stockUser = register("familia-stats-stock@example.com", "Familia Stats Stock");
        mockMvc.perform(post("/api/v1/families/{familyId}/stock-items", stockUser.familyId())
                        .header("Authorization", "Bearer " + stockUser.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Arroz", "quantity": 2, "unit": "kg"}
                                """))
                .andExpect(status().isCreated());
        assertStatsHasActivity(stockUser);

        RegisteredUser menuUser = register("familia-stats-menu@example.com", "Familia Stats Menu");
        mockMvc.perform(post("/api/v1/families/{familyId}/menu-items", menuUser.familyId())
                        .header("Authorization", "Bearer " + menuUser.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plannedDate": "2026-07-05", "mealType": "LUNCH", "note": "Comida familiar"}
                                """))
                .andExpect(status().isCreated());
        assertStatsHasActivity(menuUser);

        RegisteredUser shoppingUser = register("familia-stats-shopping@example.com", "Familia Stats Shopping");
        mockMvc.perform(post("/api/v1/families/{familyId}/shopping-lists", shoppingUser.familyId())
                        .header("Authorization", "Bearer " + shoppingUser.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Compra semanal", "plannedFrom": "2026-07-05", "plannedTo": "2026-07-06", "completed": false}
                                """))
                .andExpect(status().isCreated());
        assertStatsHasActivity(shoppingUser);
    }

    @Test
    void statsLastActivityCoversFavoritesIndependentlyFromRecipeTimestamp() throws Exception {
        RegisteredUser user = register("familia-stats-favorite@example.com", "Familia Stats Favorite");
        String recipeId = read(createRecipe(user, "Receta favorita").andReturn(), "id");
        jdbcTemplate.update(
                "UPDATE recipes SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                recipeId
        );

        mockMvc.perform(post("/api/v1/families/{familyId}/favorite-recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipeId": "%s"}
                                """.formatted(recipeId)))
                .andExpect(status().isCreated());

        MvcResult stats = mockMvc.perform(get("/api/v1/families/{familyId}/stats", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(stats.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Instant lastActivity = Instant.parse(body.get("lastActivityAt").asText());
        Assertions.assertTrue(lastActivity.isAfter(Instant.parse("2020-01-01T00:00:00Z")));
    }

    private String registerAndReadAccessToken(String email, String familyName) throws Exception {
        return register(email, familyName).accessToken();
    }

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
                response.get("family").get("id").asText()
        );
    }

    private org.springframework.test.web.servlet.ResultActions createRecipe(RegisteredUser user, String title)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "description": "Para stats"}
                                """.formatted(title)))
                .andExpect(status().isCreated());
    }

    private void assertStatsHasActivity(RegisteredUser user) throws Exception {
        mockMvc.perform(get("/api/v1/families/{familyId}/stats", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.notNullValue()));
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private record RegisteredUser(String accessToken, String familyId) {
    }
}
