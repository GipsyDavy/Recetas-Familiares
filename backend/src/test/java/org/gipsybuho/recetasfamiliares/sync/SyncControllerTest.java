package org.gipsybuho.recetasfamiliares.sync;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pullsFamilyRecipeChangesWithDeletedContent() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-owner"), "Familia Sync");
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
        RegisteredUser first = register(uniqueEmail("sync-one"), "Familia Sync Uno");
        RegisteredUser second = register(uniqueEmail("sync-two"), "Familia Sync Dos");

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request_error"));
    }

    @Test
    void pushesOfflineRecipeContentAndReturnsServerState() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-push"), "Familia Push");
        String recipeId = UUID.randomUUID().toString();
        String ingredientId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();

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
        RegisteredUser user = register(uniqueEmail("sync-push-delete"), "Familia Push Delete");
        String recipeId = UUID.randomUUID().toString();
        String ingredientId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();

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
        RegisteredUser user = register(uniqueEmail("sync-stock"), "Familia Sync Stock");
        String stockItemId = UUID.randomUUID().toString();

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
    void pushesAndPullsMenuItems() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-menu"), "Familia Sync Menu");
        String recipeId = UUID.randomUUID().toString();
        String menuItemId = UUID.randomUUID().toString();
        String ingredientId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();

        pushRecipeGraph(user, recipeId, ingredientId, stepId);

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "menuItems": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "plannedDate": "2026-06-01",
                                      "mealType": "LUNCH",
                                      "note": "Comida del lunes",
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(menuItemId, recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuItems.length()").value(1))
                .andExpect(jsonPath("$.menuItems[0].id").value(menuItemId))
                .andExpect(jsonPath("$.menuItems[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.menuItems[0].recipeTitle").value("Caldo offline"))
                .andExpect(jsonPath("$.menuItems[0].mealType").value("LUNCH"))
                .andExpect(jsonPath("$.menuItems[0].deleted").value(false));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "menuItems": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ]
                                }
                                """.formatted(menuItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuItems[0].id").value(menuItemId))
                .andExpect(jsonPath("$.menuItems[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuItems.length()").value(1))
                .andExpect(jsonPath("$.menuItems[0].id").value(menuItemId))
                .andExpect(jsonPath("$.menuItems[0].deleted").value(true));
    }

    @Test
    void pushesAndPullsShoppingListsAndItems() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-shopping"), "Familia Sync Compra");
        String shoppingListId = UUID.randomUUID().toString();
        String itemId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "shoppingLists": [
                                    {
                                      "id": "%s",
                                      "name": "Compra offline",
                                      "plannedFrom": "2026-06-01",
                                      "plannedTo": "2026-06-07",
                                      "note": "Semana planificada",
                                      "completed": false,
                                      "deleted": false
                                    }
                                  ],
                                  "shoppingListItems": [
                                    {
                                      "id": "%s",
                                      "shoppingListId": "%s",
                                      "position": 1,
                                      "name": "Pan",
                                      "quantity": 2,
                                      "unit": "ud",
                                      "checked": false,
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(shoppingListId, itemId, shoppingListId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shoppingLists.length()").value(1))
                .andExpect(jsonPath("$.shoppingLists[0].id").value(shoppingListId))
                .andExpect(jsonPath("$.shoppingLists[0].name").value("Compra offline"))
                .andExpect(jsonPath("$.shoppingListItems.length()").value(1))
                .andExpect(jsonPath("$.shoppingListItems[0].id").value(itemId))
                .andExpect(jsonPath("$.shoppingListItems[0].shoppingListId").value(shoppingListId));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "shoppingLists": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ],
                                  "shoppingListItems": []
                                }
                                """.formatted(shoppingListId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shoppingLists[0].id").value(shoppingListId))
                .andExpect(jsonPath("$.shoppingLists[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shoppingLists.length()").value(1))
                .andExpect(jsonPath("$.shoppingLists[0].id").value(shoppingListId))
                .andExpect(jsonPath("$.shoppingLists[0].deleted").value(true))
                .andExpect(jsonPath("$.shoppingListItems.length()").value(1))
                .andExpect(jsonPath("$.shoppingListItems[0].id").value(itemId))
                .andExpect(jsonPath("$.shoppingListItems[0].deleted").value(true));
    }

    @Test
    void pushesAndPullsFavoriteRecipes() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-favorite"), "Familia Sync Favoritos");
        String recipeId = UUID.randomUUID().toString();
        String ingredientId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();
        String favoriteId = UUID.randomUUID().toString();

        pushRecipeGraph(user, recipeId, ingredientId, stepId);

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "favoriteRecipes": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(favoriteId, recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteRecipes.length()").value(1))
                .andExpect(jsonPath("$.favoriteRecipes[0].id").value(favoriteId))
                .andExpect(jsonPath("$.favoriteRecipes[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.favoriteRecipes[0].recipeTitle").value("Caldo offline"))
                .andExpect(jsonPath("$.favoriteRecipes[0].deleted").value(false));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "favoriteRecipes": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ]
                                }
                                """.formatted(favoriteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteRecipes[0].id").value(favoriteId))
                .andExpect(jsonPath("$.favoriteRecipes[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteRecipes.length()").value(1))
                .andExpect(jsonPath("$.favoriteRecipes[0].id").value(favoriteId))
                .andExpect(jsonPath("$.favoriteRecipes[0].deleted").value(true));
    }

    @Test
    void pushesAndPullsFamilyNotes() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-note"), "Familia Sync Notas");
        String recipeId = UUID.randomUUID().toString();
        String ingredientId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();

        pushRecipeGraph(user, recipeId, ingredientId, stepId);

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "familyNotes": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "title": "Nota offline",
                                      "body": "Recuerdo creado sin conexion",
                                      "pinned": true,
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(noteId, recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyNotes.length()").value(1))
                .andExpect(jsonPath("$.familyNotes[0].id").value(noteId))
                .andExpect(jsonPath("$.familyNotes[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.familyNotes[0].recipeTitle").value("Caldo offline"))
                .andExpect(jsonPath("$.familyNotes[0].title").value("Nota offline"))
                .andExpect(jsonPath("$.familyNotes[0].pinned").value(true))
                .andExpect(jsonPath("$.familyNotes[0].deleted").value(false));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "familyNotes": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ]
                                }
                                """.formatted(noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyNotes[0].id").value(noteId))
                .andExpect(jsonPath("$.familyNotes[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familyNotes.length()").value(1))
                .andExpect(jsonPath("$.familyNotes[0].id").value(noteId))
                .andExpect(jsonPath("$.familyNotes[0].deleted").value(true));
    }

    @Test
    void pushesAndPullsRecipePhotos() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-photo"), "Familia Sync Fotos");
        String recipeId = UUID.randomUUID().toString();
        String ingredientId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();

        pushRecipeGraph(user, recipeId, ingredientId, stepId);

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "recipePhotos": [
                                    {
                                      "id": "%s",
                                      "recipeId": "%s",
                                      "position": 1,
                                      "url": "https://cdn.example.com/offline.jpg",
                                      "thumbnailUrl": "https://cdn.example.com/offline-thumb.jpg",
                                      "caption": "Foto offline",
                                      "contentType": "image/jpeg",
                                      "sizeBytes": 1234,
                                      "deleted": false
                                    }
                                  ]
                                }
                                """.formatted(photoId, recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipePhotos.length()").value(1))
                .andExpect(jsonPath("$.recipePhotos[0].id").value(photoId))
                .andExpect(jsonPath("$.recipePhotos[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.recipePhotos[0].url").value("https://cdn.example.com/offline.jpg"))
                .andExpect(jsonPath("$.recipePhotos[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.recipePhotos[0].deleted").value(false));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": [],
                                  "recipePhotos": [
                                    {
                                      "id": "%s",
                                      "deleted": true
                                    }
                                  ]
                                }
                                """.formatted(photoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipePhotos[0].id").value(photoId))
                .andExpect(jsonPath("$.recipePhotos[0].deleted").value(true));

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipePhotos.length()").value(1))
                .andExpect(jsonPath("$.recipePhotos[0].id").value(photoId))
                .andExpect(jsonPath("$.recipePhotos[0].deleted").value(true));
    }

    @Test
    void rejectsPushWhenBaseSyncVersionIsStale() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-conflict"), "Familia Sync Conflict");
        String recipeId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "%s",
                                      "title": "Version inicial",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].syncVersion").value(1));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "%s",
                                      "baseSyncVersion": 0,
                                      "title": "Cambio obsoleto",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [
                                    {
                                      "id": "%s",
                                      "baseSyncVersion": 1,
                                      "title": "Cambio aceptado",
                                      "deleted": false
                                    }
                                  ],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes[0].title").value("Cambio aceptado"))
                .andExpect(jsonPath("$.recipes[0].syncVersion").value(2));
    }

    @Test
    void blocksSyncPushForAnotherFamily() throws Exception {
        RegisteredUser first = register(uniqueEmail("sync-push-one"), "Familia Push Uno");
        RegisteredUser second = register(uniqueEmail("sync-push-two"), "Familia Push Dos");

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
        RegisteredUser user = register(uniqueEmail("sync-push-validation"), "Familia Push Validacion");

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
        RegisteredUser user = register(uniqueEmail("sync-push-uuid-validation"), "Familia Push Uuid");

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

    @Test
    void pullWithLimitPaginatesWithoutLosingRows() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-paged"), "Familia Paged");
        java.util.Set<String> expectedIds = new java.util.HashSet<>();
        for (int i = 1; i <= 3; i++) {
            MvcResult created = createRecipe(user, "Receta paginada " + i).andReturn();
            expectedIds.add(read(created, "id"));
        }

        java.util.Set<String> seenIds = new java.util.HashSet<>();
        String since = "1970-01-01T00:00:00Z";
        boolean hasMore = true;
        int pages = 0;
        while (hasMore) {
            if (++pages > 5) {
                throw new AssertionError("Pagination did not terminate");
            }
            MvcResult page = mockMvc.perform(
                            get("/api/v1/families/{familyId}/sync/pull?since={since}&limit=1", user.familyId(), since)
                                    .header("Authorization", "Bearer " + user.accessToken()))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(page.getResponse().getContentAsString(StandardCharsets.UTF_8));
            body.get("recipes").forEach(recipe -> seenIds.add(recipe.get("id").asText()));
            hasMore = body.get("hasMore").asBoolean();
            if (hasMore) {
                org.junit.jupiter.api.Assertions.assertFalse(body.get("nextSince").isNull());
                since = body.get("nextSince").asText();
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(expectedIds, seenIds);
        org.junit.jupiter.api.Assertions.assertTrue(pages >= 2, "limit=1 with 3 recipes should need several pages");
    }

    @Test
    void pullWithLimitKeepsWholeUpdatedAtGroupWhenAllRowsShareTimestamp() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-paged-same-time"), "Familia Paged Same Time");
        java.util.Set<String> expectedIds = new java.util.HashSet<>();
        for (int i = 1; i <= 3; i++) {
            MvcResult created = createRecipe(user, "Receta mismo timestamp " + i).andReturn();
            expectedIds.add(read(created, "id"));
        }
        Instant boundary = Instant.parse("2026-07-05T10:15:30Z");
        expectedIds.forEach(id -> jdbcTemplate.update(
                "UPDATE recipes SET updated_at = ? WHERE id = ?",
                Timestamp.from(boundary),
                id
        ));

        MvcResult page = mockMvc.perform(get(
                                "/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z&limit=1",
                                user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(page.getResponse().getContentAsString(StandardCharsets.UTF_8));
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        body.get("recipes").forEach(recipe -> seenIds.add(recipe.get("id").asText()));
        org.junit.jupiter.api.Assertions.assertEquals(expectedIds, seenIds);
    }

    @Test
    void pullWithoutLimitKeepsFullResponseContract() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-nolimit"), "Familia NoLimit");
        createRecipe(user, "Receta completa");

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextSince").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void pullWithoutLimitUsesServerDefaultPageSize() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-default-limit"), "Familia Default Limit");
        Instant base = Instant.parse("2026-07-11T10:00:00Z");
        for (int i = 0; i <= SyncService.DEFAULT_PULL_LIMIT; i++) {
            MvcResult created = createRecipe(user, "Receta default limit " + i).andReturn();
            jdbcTemplate.update(
                    "UPDATE recipes SET updated_at = ? WHERE id = ?",
                    Timestamp.from(base.plusSeconds(i)),
                    read(created, "id")
            );
        }

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(org.hamcrest.Matchers.lessThan(SyncService.DEFAULT_PULL_LIMIT + 1)))
                .andExpect(jsonPath("$.recipes.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextSince", notNullValue()));
    }

    @Test
    void rejectsNonPositivePullLimit() throws Exception {
        RegisteredUser user = register(uniqueEmail("sync-badlimit"), "Familia BadLimit");

        mockMvc.perform(get("/api/v1/families/{familyId}/sync/pull?since=1970-01-01T00:00:00Z&limit=0", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isBadRequest());
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
    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

}
