package org.gipsybuho.recetasfamiliares.photos;

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
class RecipePhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsUpdatesAndSoftDeletesRecipePhotoMetadata() throws Exception {
        RegisteredUser user = register(uniqueEmail("photo-owner"), "Familia Fotos");
        String recipeId = read(createRecipe(user, "Tarta con foto").andReturn(), "id");
        MvcResult created = createPhoto(user, recipeId, "https://cdn.example.com/tarta.jpg").andReturn();
        String photoId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/photos", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(photoId))
                .andExpect(jsonPath("$[0].url").value("https://cdn.example.com/tarta.jpg"))
                .andExpect(jsonPath("$[0].thumbnailUrl").value("https://cdn.example.com/tarta-thumb.jpg"))
                .andExpect(jsonPath("$[0].contentType").value("image/jpeg"));

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}", user.familyId(), recipeId, photoId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": 2,
                                  "url": "https://cdn.example.com/tarta-final.webp",
                                  "thumbnailUrl": "https://cdn.example.com/tarta-final-thumb.webp",
                                  "caption": "Resultado final",
                                  "contentType": "image/webp",
                                  "sizeBytes": 2048
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.url").value("https://cdn.example.com/tarta-final.webp"))
                .andExpect(jsonPath("$.caption").value("Resultado final"))
                .andExpect(jsonPath("$.syncVersion", greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}", user.familyId(), recipeId, photoId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}", user.familyId(), recipeId, photoId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/photos?includeDeleted=true", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deleted").value(true));
    }

    @Test
    void blocksPhotoAccessToAnotherFamily() throws Exception {
        RegisteredUser first = register(uniqueEmail("photo-one"), "Familia Fotos Uno");
        RegisteredUser second = register(uniqueEmail("photo-two"), "Familia Fotos Dos");
        String secondRecipeId = read(createRecipe(second, "Foto privada").andReturn(), "id");
        String photoId = read(createPhoto(second, secondRecipeId, "https://cdn.example.com/private.jpg").andReturn(), "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/photos", second.familyId(), secondRecipeId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}", second.familyId(), secondRecipeId, photoId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesPhotoMetadata() throws Exception {
        RegisteredUser user = register(uniqueEmail("photo-validation"), "Familia Fotos Validacion");
        String recipeId = read(createRecipe(user, "Foto invalida").andReturn(), "id");

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/photos", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": 1,
                                  "url": "file:///secret.jpg",
                                  "contentType": "image/gif"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request_error"));
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Photo User",
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
                                  "description": "Receta con fotos",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 30,
                                  "difficulty": "EASY"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions createPhoto(RegisteredUser user, String recipeId, String url)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/photos", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": 1,
                                  "url": "%s",
                                  "thumbnailUrl": "https://cdn.example.com/tarta-thumb.jpg",
                                  "caption": "Foto principal",
                                  "contentType": "image/jpeg",
                                  "sizeBytes": 1024
                                }
                                """.formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.recipeId").value(recipeId))
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
