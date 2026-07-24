package org.gipsybuho.recetasfamiliares.menus;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
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
class MenuItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsUpdatesAndSoftDeletesMenuItem() throws Exception {
        RegisteredUser user = register(uniqueEmail("menu-owner"), "Familia Menu");
        String recipeId = read(createRecipe(user, "Lentejas del lunes").andReturn(), "id");
        MvcResult created = createMenuItem(user, recipeId, "2026-06-01", "LUNCH").andReturn();
        String menuItemId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/menu-items?weekStart=2026-06-01", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(menuItemId))
                .andExpect(jsonPath("$.items[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.items[0].recipeTitle").value("Lentejas del lunes"))
                .andExpect(jsonPath("$.items[0].mealType").value("LUNCH"));

        mockMvc.perform(put("/api/v1/families/{familyId}/menu-items/{menuItemId}", user.familyId(), menuItemId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "plannedDate": "2026-06-02",
                                  "mealType": "DINNER",
                                  "note": "Cena ligera"
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedDate").value("2026-06-02"))
                .andExpect(jsonPath("$.mealType").value("DINNER"))
                .andExpect(jsonPath("$.note").value("Cena ligera"))
                .andExpect(jsonPath("$.syncVersion", greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/v1/families/{familyId}/menu-items/{menuItemId}", user.familyId(), menuItemId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/menu-items/{menuItemId}", user.familyId(), menuItemId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksMenuAccessToAnotherFamilyAndRejectsForeignRecipe() throws Exception {
        RegisteredUser first = register(uniqueEmail("menu-one"), "Familia Menu Uno");
        RegisteredUser second = register(uniqueEmail("menu-two"), "Familia Menu Dos");
        String secondRecipeId = read(createRecipe(second, "Tortilla privada").andReturn(), "id");
        String menuItemId = read(createMenuItem(second, secondRecipeId, "2026-06-01", "DINNER").andReturn(), "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/menu-items", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/families/{familyId}/menu-items/{menuItemId}", second.familyId(), menuItemId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/families/{familyId}/menu-items", first.familyId())
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "plannedDate": "2026-06-01",
                                  "mealType": "LUNCH"
                                }
                                """.formatted(secondRecipeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesMenuInput() throws Exception {
        RegisteredUser user = register(uniqueEmail("menu-validation"), "Familia Menu Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/menu-items", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plannedDate": null,
                                  "mealType": null
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
                                  "displayName": "Menu User",
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
                                  "description": "Receta para planificar",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 30,
                                  "difficulty": "EASY"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions createMenuItem(
            RegisteredUser user,
            String recipeId,
            String plannedDate,
            String mealType
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/menu-items", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "plannedDate": "%s",
                                  "mealType": "%s",
                                  "note": "Plan familiar"
                                }
                                """.formatted(recipeId, plannedDate, mealType)))
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
