package org.gipsybuho.recetasfamiliares.shopping;

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
class ShoppingListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsUpdatesItemsAndSoftDeletesShoppingList() throws Exception {
        RegisteredUser user = register(uniqueEmail("shopping-owner"), "Familia Compra");
        MvcResult created = createShoppingList(user, "Compra semanal").andReturn();
        String shoppingListId = read(created, "id");
        MvcResult itemCreated = createItem(user, shoppingListId, "Tomate").andReturn();
        String itemId = read(itemCreated, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(shoppingListId))
                .andExpect(jsonPath("$.items[0].name").value("Compra semanal"));

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items", user.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(itemId))
                .andExpect(jsonPath("$.items[0].name").value("Tomate"));

        mockMvc.perform(put("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}", user.familyId(), shoppingListId, itemId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": 2,
                                  "name": "Tomate pera",
                                  "quantity": 1.500,
                                  "unit": "kg",
                                  "checked": true,
                                  "note": "Para sofrito"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.name").value("Tomate pera"))
                .andExpect(jsonPath("$.checked").value(true))
                .andExpect(jsonPath("$.syncVersion", greaterThanOrEqualTo(1)));

        mockMvc.perform(put("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}", user.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Compra fin de semana",
                                  "plannedFrom": "2026-06-05",
                                  "plannedTo": "2026-06-07",
                                  "note": "Invitados",
                                  "completed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Compra fin de semana"))
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(delete("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}", user.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}", user.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}", user.familyId(), shoppingListId, itemId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksShoppingAccessToAnotherFamily() throws Exception {
        RegisteredUser first = register(uniqueEmail("shopping-one"), "Familia Compra Uno");
        RegisteredUser second = register(uniqueEmail("shopping-two"), "Familia Compra Dos");
        String shoppingListId = read(createShoppingList(second, "Compra privada").andReturn(), "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}", second.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesShoppingInput() throws Exception {
        RegisteredUser user = register(uniqueEmail("shopping-validation"), "Familia Compra Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/shopping-lists", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void generatesShoppingListFromPlannedMenuRecipes() throws Exception {
        RegisteredUser user = register(uniqueEmail("shopping-generate"), "Familia Compra Generada");
        String firstRecipeId = read(createRecipe(user, "Pasta familiar").andReturn(), "id");
        String secondRecipeId = read(createRecipe(user, "Ensalada familiar").andReturn(), "id");
        replaceIngredients(user, firstRecipeId, """
                [
                  {
                    "name": "Tomate",
                    "quantity": 2,
                    "unit": "ud"
                  },
                  {
                    "name": "Pasta",
                    "quantity": 500,
                    "unit": "g"
                  }
                ]
                """);
        replaceIngredients(user, secondRecipeId, """
                [
                  {
                    "name": "tomate",
                    "quantity": 3,
                    "unit": "ud"
                  },
                  {
                    "name": "Lechuga",
                    "quantity": 1,
                    "unit": "ud"
                  }
                ]
                """);
        createMenuItem(user, firstRecipeId, "2026-06-01", "LUNCH");
        createMenuItem(user, secondRecipeId, "2026-06-02", "DINNER");

        MvcResult generated = mockMvc.perform(post("/api/v1/families/{familyId}/shopping-lists/generate-from-menu", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Compra del menu",
                                  "startDate": "2026-06-01",
                                  "endDate": "2026-06-07",
                                  "note": "Generada desde menus"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Compra del menu"))
                .andExpect(jsonPath("$.plannedFrom").value("2026-06-01"))
                .andExpect(jsonPath("$.plannedTo").value("2026-06-07"))
                .andReturn();
        String shoppingListId = read(generated, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items", user.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Tomate"))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.items[0].unit").value("ud"))
                .andExpect(jsonPath("$.items[1].name").value("Pasta"))
                .andExpect(jsonPath("$.items[1].quantity").value(500))
                .andExpect(jsonPath("$.items[1].unit").value("g"))
                .andExpect(jsonPath("$.items[2].name").value("Lechuga"))
                .andExpect(jsonPath("$.items[2].quantity").value(1))
                .andExpect(jsonPath("$.items[2].unit").value("ud"));
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Shopping User",
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

    private org.springframework.test.web.servlet.ResultActions createShoppingList(RegisteredUser user, String name)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/shopping-lists", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "plannedFrom": "2026-06-01",
                                  "plannedTo": "2026-06-07",
                                  "note": "Semana familiar",
                                  "completed": false
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.familyId").value(user.familyId()))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions createItem(RegisteredUser user, String shoppingListId, String name)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items", user.familyId(), shoppingListId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": 1,
                                  "name": "%s",
                                  "quantity": 1,
                                  "unit": "kg",
                                  "checked": false,
                                  "note": "Mercado"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.shoppingListId").value(shoppingListId))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions createRecipe(RegisteredUser user, String title)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Receta para comprar",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 30,
                                  "difficulty": "EASY"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    private void replaceIngredients(RegisteredUser user, String recipeId, String itemsJson) throws Exception {
        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": %s
                                }
                                """.formatted(itemsJson)))
                .andExpect(status().isOk());
    }

    private void createMenuItem(RegisteredUser user, String recipeId, String plannedDate, String mealType)
            throws Exception {
        mockMvc.perform(post("/api/v1/families/{familyId}/menu-items", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "plannedDate": "%s",
                                  "mealType": "%s",
                                  "note": "Planificado"
                                }
                                """.formatted(recipeId, plannedDate, mealType)))
                .andExpect(status().isCreated());
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
