package org.gipsybuho.recetasfamiliares.photos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

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
@SpringBootTest(properties = {
        "app.upload.dir=target/test-uploads",
        "app.upload.base-url=https://cdn.recetas.test"
})
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recipePhotoOnlyAccessibleForFamilyMembers() throws Exception {
        RegisteredUser owner = register(uniqueEmail("uploads-owner"), "Familia Uploads");
        RegisteredUser outsider = register(uniqueEmail("uploads-outsider"), "Familia Ajena");

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
        RegisteredUser owner = register(uniqueEmail("avatar-owner"), "Familia Avatar");
        RegisteredUser outsider = register(uniqueEmail("avatar-outsider"), "Familia Avatar Ajena");

        String avatarPath = uploadAvatar(owner);

        mockMvc.perform(get(avatarPath).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mockMvc.perform(get(avatarPath).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonUuidUploadFilenames() throws Exception {
        RegisteredUser user = register(uniqueEmail("uploads-names"), "Familia Nombres");

        mockMvc.perform(get("/uploads/application.yml")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void orphanFilesWithoutDatabaseRecordAreNotServed() throws Exception {
        RegisteredUser user = register(uniqueEmail("uploads-orphan"), "Familia Huerfana");
        java.nio.file.Path orphan = java.nio.file.Path.of("target/test-uploads",
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg");
        java.nio.file.Files.createDirectories(orphan.getParent());
        java.nio.file.Files.write(orphan, validJpeg());

        mockMvc.perform(get("/uploads/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void externalPhotoUrlWithExactLocalUrlDoesNotGrantFileAccess() throws Exception {
        RegisteredUser owner = register(uniqueEmail("uploads-real-owner"), "Familia Real");
        RegisteredUser attacker = register(uniqueEmail("uploads-suffix-attacker"), "Familia Sufijo");

        String ownerRecipeId = createRecipe(owner);
        String photoPath = uploadRecipePhoto(owner, ownerRecipeId);

        String attackerRecipeId = createRecipe(attacker);
        createExternalPhotoMetadata(attacker, attackerRecipeId, "https://cdn.recetas.test" + photoPath);

        mockMvc.perform(get(photoPath).header("Authorization", "Bearer " + attacker.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(photoPath).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void privateMessageImageOnlyAccessibleForConversationParticipants() throws Exception {
        RegisteredUser owner = register(uniqueEmail("upload-dm-owner"), "Familia Upload DM");
        String guestEmail = uniqueEmail("upload-dm-guest");
        RegisteredUser guest = register(guestEmail, "Familia Upload DM Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());
        RegisteredUser outsider = register(uniqueEmail("upload-dm-outsider"), "Familia Upload DM Otra");

        MvcResult conversationResult = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String conversationId = objectMapper.readTree(
                        conversationResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("conversationId").asText();

        MvcResult imageResult = mockMvc.perform(multipart(
                                "/api/v1/families/{familyId}/conversations/{conversationId}/messages/images",
                                owner.familyId(), conversationId)
                        .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", validJpeg()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String attachmentUrl = objectMapper.readTree(
                        imageResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("attachments").get(0).get("url").asText();
        String path = attachmentUrl.substring(attachmentUrl.indexOf("/uploads/"));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk());
        // outsider comparte la app pero no la conversacion: debe ser 404, aunque
        // no comparta familia con ninguno de los dos participantes.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksPrivateImageAccessAfterParticipantLeavesFamily() throws Exception {
        RegisteredUser owner = register(uniqueEmail("upload-dm-leave-owner"), "Familia Upload DM Leave");
        String guestEmail = uniqueEmail("upload-dm-leave-guest");
        RegisteredUser guest = register(guestEmail, "Familia Upload DM Leave Guest");
        mockMvc.perform(post("/api/v1/families/{familyId}/members", owner.familyId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "role": "MEMBER"}
                                """.formatted(guestEmail)))
                .andExpect(status().isCreated());

        MvcResult conversationResult = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/conversations/with/{otherUserId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String conversationId = objectMapper.readTree(
                        conversationResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("conversationId").asText();

        MvcResult imageResult = mockMvc.perform(multipart(
                                "/api/v1/families/{familyId}/conversations/{conversationId}/messages/images",
                                owner.familyId(), conversationId)
                        .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", validJpeg()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String attachmentUrl = objectMapper.readTree(
                        imageResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("attachments").get(0).get("url").asText();
        String path = attachmentUrl.substring(attachmentUrl.indexOf("/uploads/"));

        // Todavia participante y todavia miembro de la familia: acceso normal.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isOk());

        // Se expulsa a guest de la familia (owner es quien administra).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/families/{familyId}/members/{userId}",
                        owner.familyId(), guest.userId())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        // guest sigue siendo tecnicamente participante de la conversacion (esa
        // relacion nunca se borra), pero ya no es miembro de la familia: debe
        // perder el acceso a la imagen, igual que ya ocurre por REST.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + guest.accessToken()))
                .andExpect(status().isNotFound());
    }

    private String uploadRecipePhoto(RegisteredUser user, String recipeId) throws Exception {
        MvcResult result = mockMvc.perform(multipart(
                        "/api/v1/families/{familyId}/recipes/{recipeId}/photos/upload",
                        user.familyId(), recipeId)
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", validJpeg()))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String url = read(result, "url");
        return URI.create(url).getPath();
    }

    private void createExternalPhotoMetadata(RegisteredUser user, String recipeId, String url) throws Exception {
        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/photos", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": 1,
                                  "url": "%s",
                                  "thumbnailUrl": "%s",
                                  "contentType": "image/jpeg",
                                  "sizeBytes": 1024
                                }
                                """.formatted(url, url)))
                .andExpect(status().isCreated());
    }

    private String uploadAvatar(RegisteredUser user) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.jpg", "image/jpeg", validJpeg()))
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
                response.get("family").get("id").asText(),
                response.get("user").get("id").asText()
        );
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get(field).asText();
    }

    private byte[] validJpeg() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IllegalStateException("No JPEG writer available");
        }
        return out.toByteArray();
    }

    private record RegisteredUser(String accessToken, String familyId, String userId) {
    }
}
