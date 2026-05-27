package org.gipsybuho.recetasfamiliares.notes;

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
class FamilyNoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsUpdatesAndSoftDeletesFamilyNote() throws Exception {
        RegisteredUser user = register("note-owner@example.com", "Familia Notas");
        String recipeId = read(createRecipe(user, "Croquetas de la abuela").andReturn(), "id");
        MvcResult created = createNote(user, recipeId, "Recuerdo familiar").andReturn();
        String noteId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/notes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(noteId))
                .andExpect(jsonPath("$.items[0].recipeId").value(recipeId))
                .andExpect(jsonPath("$.items[0].recipeTitle").value("Croquetas de la abuela"));

        mockMvc.perform(put("/api/v1/families/{familyId}/notes/{noteId}", user.familyId(), noteId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "title": "Recuerdo actualizado",
                                  "body": "Siempre las hacia los domingos.",
                                  "pinned": false
                                }
                                """.formatted(recipeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Recuerdo actualizado"))
                .andExpect(jsonPath("$.body").value("Siempre las hacia los domingos."))
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.syncVersion", greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/v1/families/{familyId}/notes/{noteId}", user.familyId(), noteId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/notes/{noteId}", user.familyId(), noteId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksNoteAccessToAnotherFamilyAndRejectsForeignRecipe() throws Exception {
        RegisteredUser first = register("note-one@example.com", "Familia Notas Uno");
        RegisteredUser second = register("note-two@example.com", "Familia Notas Dos");
        String secondRecipeId = read(createRecipe(second, "Receta privada").andReturn(), "id");
        String noteId = read(createNote(second, secondRecipeId, "Nota privada").andReturn(), "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/notes", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/families/{familyId}/notes/{noteId}", second.familyId(), noteId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/families/{familyId}/notes", first.familyId())
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "title": "No deberia",
                                  "body": "Receta de otra familia",
                                  "pinned": false
                                }
                                """.formatted(secondRecipeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesNoteInput() throws Exception {
        RegisteredUser user = register("note-validation@example.com", "Familia Notas Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/notes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "body": ""
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
                                  "displayName": "Note User",
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
                                  "description": "Receta con memoria",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 30,
                                  "difficulty": "EASY"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions createNote(RegisteredUser user, String recipeId, String title)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/notes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": "%s",
                                  "title": "%s",
                                  "body": "La receta favorita de los domingos.",
                                  "pinned": true
                                }
                                """.formatted(recipeId, title)))
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
}
