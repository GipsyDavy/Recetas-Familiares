package org.gipsybuho.recetasfamiliares.favorites;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class FavoriteRecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsRestoresAndSoftDeletesFavoriteRecipe() throws Exception {
        RegisteredUser user = register(uniqueEmail("favorite-owner"), "Familia Favoritos");
        String recipeId = read(createRecipe(user, "Arroz favorito").andReturn(), "id");
        MvcResult created = createFavorite(user, recipeId).andReturn();
        String favoriteId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/favorite-recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(favoriteId))
                .andExpect(jsonPath("$.items[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.items[0].recipeTitle").value("Arroz favorito"));

        mockMvc.perform(delete("/api/v1/families/{familyId}/favorite-recipes/{favoriteId}", user.familyId(), favoriteId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/favorite-recipes/{favoriteId}", user.familyId(), favoriteId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());

        createFavorite(user, recipeId)
                .andExpect(jsonPath("$.id").value(favoriteId))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void blocksFavoriteAccessToAnotherFamilyAndRejectsForeignRecipe() throws Exception {
        RegisteredUser first = register(uniqueEmail("favorite-one"), "Familia Favoritos Uno");
        RegisteredUser second = register(uniqueEmail("favorite-two"), "Familia Favoritos Dos");
        String secondRecipeId = read(createRecipe(second, "Receta privada").andReturn(), "id");
        String favoriteId = read(createFavorite(second, secondRecipeId).andReturn(), "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/favorite-recipes", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/families/{familyId}/favorite-recipes/{favoriteId}", second.familyId(), favoriteId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/families/{familyId}/favorite-recipes", first.familyId())
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s"
                                }
                                """.formatted(secondRecipeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesFavoriteInput() throws Exception {
        RegisteredUser user = register(uniqueEmail("favorite-validation"), "Familia Favoritos Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/favorite-recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Favorite User",
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
                                {
                                  "title": "%s",
                                  "description": "Receta favorita",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 30,
                                  "difficulty": "EASY"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions createFavorite(RegisteredUser user, String recipeId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/favorite-recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s"
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.familyId").value(user.familyId()))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private record RegisteredUser(String accessToken, String familyId) {
    }
    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

}
