package org.gipsybuho.recetasfamiliares.photos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.upload.dir=target/test-uploads")
@AutoConfigureMockMvc
class UploadControllerTest {

    private static final byte[] MINIMAL_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recipePhotoOnlyAccessibleForFamilyMembers() throws Exception {
        RegisteredUser owner = register("uploads-owner@example.com", "Familia Uploads");
        RegisteredUser outsider = register("uploads-outsider@example.com", "Familia Ajena");

        String recipeId = createRecipe(owner);
        String photoPath = uploadRecipePhoto(owner, recipeId);

        mockMvc.perform(get(photoPath).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mockMvc.perform(get(photoPath).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(photoPath))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarOnlyAccessibleForOwnerAndFamilyMembers() throws Exception {
        RegisteredUser owner = register("avatar-owner@example.com", "Familia Avatar");
        RegisteredUser outsider = register("avatar-outsider@example.com", "Familia Avatar Ajena");

        String avatarPath = uploadAvatar(owner);

        mockMvc.perform(get(avatarPath).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mockMvc.perform(get(avatarPath).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonUuidUploadFilenames() throws Exception {
        RegisteredUser user = register("uploads-names@example.com", "Familia Nombres");

        mockMvc.perform(get("/uploads/application.yml")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void orphanFilesWithoutDatabaseRecordAreNotServed() throws Exception {
        RegisteredUser user = register("uploads-orphan@example.com", "Familia Huerfana");
        java.nio.file.Path orphan = java.nio.file.Path.of("target/test-uploads",
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg");
        java.nio.file.Files.createDirectories(orphan.getParent());
        java.nio.file.Files.write(orphan, MINIMAL_JPEG);

        mockMvc.perform(get("/uploads/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    private String uploadRecipePhoto(RegisteredUser user, String recipeId) throws Exception {
        MvcResult result = mockMvc.perform(multipart(
                                "/api/v1/families/{familyId}/recipes/{recipeId}/photos/upload",
                                user.familyId(), recipeId)
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", MINIMAL_JPEG))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String url = read(result, "url");
        return URI.create(url).getPath();
    }

    private String uploadAvatar(RegisteredUser user) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.jpg", "image/jpeg", MINIMAL_JPEG))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String url = read(result, "avatarUrl");
        return URI.create(url).getPath();
    }

    private String createRecipe(RegisteredUser user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Receta con foto",
                                  "description": "Para probar uploads",
                                  "servings": 2,
                                  "prepMinutes": 5,
                                  "cookMinutes": 10,
                                  "difficulty": "EASY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "id");
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Upload User",
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

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private record RegisteredUser(String accessToken, String familyId) {
    }
}
