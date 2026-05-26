package org.gipsybuho.recetasfamiliares.sync;

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
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pullsFamilyRecipeChangesWithDeletedContent() throws Exception {
        RegisteredUser user = register("sync-owner@example.com", "Familia Sync");
        MvcResult created = createRecipe(user, "Potaje familiar").andReturn();
        String recipeId = read(created, "id");

        replaceIngredient(user, recipeId, "Garbanzos");
        replaceStep(user, recipeId, "Cocer a fuego lento");

        mockMvc.perform(delete("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime", notNullValue()))
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].id").value(recipeId))
                .andExpect(jsonPath("$.recipes[0].deleted").value(true))
                .andExpect(jsonPath("$.ingredients.length()").value(1))
                .andExpect(jsonPath("$.ingredients[0].name").value("Garbanzos"))
                .andExpect(jsonPath("$.ingredients[0].deleted").value(true))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].instruction").value("Cocer a fuego lento"))
                .andExpect(jsonPath("$.steps[0].deleted").value(true));
    }

    @Test
    void blocksSyncPullForAnotherFamily() throws Exception {
        RegisteredUser first = register("sync-one@example.com", "Familia Sync Uno");
        RegisteredUser second = register("sync-two@example.com", "Familia Sync Dos");

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request_error"));
    }

    @Test
    void pushesOfflineRecipeContentAndReturnsServerState() throws Exception {
        RegisteredUser user = register("sync-push@example.com", "Familia Push");
        String recipeId = "11111111-1111-4111-8111-111111111111";
        String ingredientId = "22222222-2222-4222-8222-222222222222";
        String stepId = "33333333-3333-4333-8333-333333333333";

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "%s",
                                      "title": "Pure familiar",
                                      "description": "Creado offline",
                                      "servings": 3,
                                      "prepMinutes": 5,
                                      "cookMinutes": 20,
                                      "difficulty": "EASY",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "position": 1,
                                      "name": "Patata",
                                      "quantity": 500,
                                      "unit": "g",
                                      "deleted": false
                                    }
                                  ],
                                  "steps": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "position": 1,
                                      "instruction": "Chafar la patata",
                                      "timerMinutes": 2,
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(recipeId, ingredientId, recipeId, stepId, recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime", notNullValue()))
                .andExpect(jsonPath("$.recipes[0].id").value(recipeId))
                .andExpect(jsonPath("$.recipes[0].title").value("Pure familiar"))
                .andExpect(jsonPath("$.recipes[0].syncVersion").value(1))
                .andExpect(jsonPath("$.ingredients[0].id").value(ingredientId))
                .andExpect(jsonPath("$.ingredients[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.steps[0].id").value(stepId))
                .andExpect(jsonPath("$.steps[0].recipeId").value(recipeId));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].id").value(recipeId))
                .andExpect(jsonPath("$.ingredients[0].id").value(ingredientId))
                .andExpect(jsonPath("$.steps[0].id").value(stepId));
    }

    @Test
    void pushesRecipeDeleteAndReturnsContentTombstones() throws Exception {
        RegisteredUser user = register("sync-push-delete@example.com", "Familia Push Delete");
        String recipeId = "44444444-4444-4444-8444-444444444444";
        String ingredientId = "55555555-5555-4555-8555-555555555555";
        String stepId = "66666666-6666-4666-8666-666666666666";

        pushRecipeGraph(user, recipeId, ingredientId, stepId);

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].id").value(recipeId))
                .andExpect(jsonPath("$.recipes[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].deleted").value(true))
                .andExpect(jsonPath("$.ingredients[0].id").value(ingredientId))
                .andExpect(jsonPath("$.ingredients[0].deleted").value(true))
                .andExpect(jsonPath("$.steps[0].id").value(stepId))
                .andExpect(jsonPath("$.steps[0].deleted").value(true));
    }

    @Test
    void pushesAndPullsStockItems() throws Exception {
        RegisteredUser user = register("sync-stock@example.com", "Familia Sync Stock");
        String stockItemId = "88888888-8888-4888-8888-888888888888";

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "stockItems": [
                                    {
                                      "id": "%s",
                                      "name": "Aceite",
                                      "quantity": 1,
                                      "unit": "l",
                                      "lowStockThreshold": 0.250,
                                      "expiresAt": "2026-12-31",
                                      "note": "Virgen extra",
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(stockItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockItems.length()").value(1))
                .andExpect(jsonPath("$.stockItems[0].id").value(stockItemId))
                .andExpect(jsonPath("$.stockItems[0].name").value("Aceite"))
                .andExpect(jsonPath("$.stockItems[0].deleted").value(false));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "stockItems": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ]
                                }
                                """.formatted(stockItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockItems[0].id").value(stockItemId))
                .andExpect(jsonPath("$.stockItems[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockItems.length()").value(1))
                .andExpect(jsonPath("$.stockItems[0].id").value(stockItemId))
                .andExpect(jsonPath("$.stockItems[0].deleted").value(true));
    }

    @Test
    void blocksSyncPushForAnotherFamily() throws Exception {
        RegisteredUser first = register("sync-push-one@example.com", "Familia Push Uno");
        RegisteredUser second = register("sync-push-two@example.com", "Familia Push Dos");

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request_error"));
    }

    @Test
    void validatesSyncPushPayload() throws Exception {
        RegisteredUser user = register("sync-push-validation@example.com", "Familia Push Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "77777777-7777-4777-8777-777777777777",
                                      "title": "",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request_error"));
    }

    @Test
    void rejectsNonUuidV4SyncIds() throws Exception {
        RegisteredUser user = register("sync-push-uuid-validation@example.com", "Familia Push Uuid");

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "not-a-uuid",
                                      "title": "Offline",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [],
                                  "steps": []
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
                                  "displayName": "Sync User",
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
                                  "description": "Receta para sincronizar",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 40,
                                  "difficulty": "EASY"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated());
    }

    private void replaceIngredient(RegisteredUser user, String recipeId, String name) throws Exception {
        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "name": "%s",
                                      "quantity": 250,
                                      "unit": "g"
                                    }
                                  ]
                                }
                                """.formatted(name)))
                .andExpect(status().isOk());
    }

    private void replaceStep(RegisteredUser user, String recipeId, String instruction) throws Exception {
        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/steps", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "instruction": "%s",
                                      "timerMinutes": 40
                                    }
                                  ]
                                }
                                """.formatted(instruction)))
                .andExpect(status().isOk());
    }

    private void pushRecipeGraph(RegisteredUser user, String recipeId, String ingredientId, String stepId)
            throws Exception {
        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "%s",
                                      "title": "Caldo offline",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "position": 1,
                                      "name": "Agua",
                                      "quantity": 1,
                                      "unit": "l",
                                      "deleted": false
                                    }
                                  ],
                                  "steps": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "position": 1,
                                      "instruction": "Hervir",
                                      "timerMinutes": 15,
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(recipeId, ingredientId, recipeId, stepId, recipeId)))
                .andExpect(status().isOk());
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private record RegisteredUser(String accessToken, String familyId) {
    }
}
