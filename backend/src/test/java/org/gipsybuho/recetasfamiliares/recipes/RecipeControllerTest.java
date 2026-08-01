package org.gipsybuho.recetasfamiliares.recipes;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
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
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecipeIngredientRepository ingredientRepository;

    @Autowired
    private RecipeStepRepository stepRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsAndListsRecipesForAuthenticatedFamilyMember() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-owner"), "Familia Recetas");

        createRecipe(user, "Tortilla familiar")
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.familyId").value(user.familyId()))
                .andExpect(jsonPath("$.title").value("Tortilla familiar"))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.deleted").value(false));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Tortilla familiar"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void getsUpdatesAndSoftDeletesRecipe() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-crud"), "Familia CRUD");
        MvcResult created = createRecipe(user, "Gazpacho familiar").andReturn();
        String recipeId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Gazpacho familiar"));

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Gazpacho de verano",
                                  "description": "Version actualizada",
                                  "servings": 6,
                                  "prepMinutes": 15,
                                  "cookMinutes": 0,
                                  "difficulty": "MEDIUM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Gazpacho de verano"))
                .andExpect(jsonPath("$.description").value("Version actualizada"))
                .andExpect(jsonPath("$.servings").value(6))
                .andExpect(jsonPath("$.syncVersion", greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void blocksRecipeAccessToAnotherFamily() throws Exception {
        RegisteredUser first = register(uniqueEmail("recipes-one"), "Familia Uno");
        RegisteredUser second = register(uniqueEmail("recipes-two"), "Familia Dos");

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Receta ajena"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request_error"));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request_error"));

        MvcResult secondRecipe = createRecipe(second, "Receta privada").andReturn();
        String secondRecipeId = read(secondRecipe, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", second.familyId(), secondRecipeId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}", second.familyId(), secondRecipeId)
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Intento ajeno"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/families/{familyId}/recipes/{recipeId}", second.familyId(), secondRecipeId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsFamilyMembersToReadButOnlyOwnersAndAdminsCanWrite() throws Exception {
        RegisteredUser owner = register(uniqueEmail("recipes-role-owner"), "Familia Roles");
        String memberEmail = uniqueEmail("recipes-role-member");
        RegisteredUser member = register(memberEmail, "Familia Invitada");
        addFamilyMember(owner.familyId(), memberEmail, FamilyRole.MEMBER);
        createRecipe(owner, "Receta visible para miembro");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", owner.familyId())
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Receta visible para miembro"));

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes", owner.familyId())
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Intento de miembro"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/families/{familyId}/sync/push", owner.familyId())
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipes": [],
                                  "ingredients": [],
                                  "steps": []
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesRecipeInput() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-validation"), "Familia Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "servings": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void copiesRecipeContentToAnotherOwnedFamily() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-copy-owner"), "Familia Origen");
        MvcResult source = createRecipe(user, "Paella familiar").andReturn();
        String recipeId = read(source, "id");
        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"name": "Arroz", "quantity": 400, "unit": "g"},
                                    {"name": "Caldo", "quantity": 1, "unit": "l"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/steps", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"instruction": "Sofreir la base"},
                                    {"instruction": "Cocer el arroz", "timerMinutes": 18}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        String targetFamilyId = read(createFamily(user, "Familia Destino").andReturn(), "id");

        MvcResult copied = mockMvc.perform(post(
                        "/api/v1/families/{familyId}/recipes/{recipeId}/copy",
                        user.familyId(),
                        recipeId
                )
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetFamilyId": "%s"}
                                """.formatted(targetFamilyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(recipeId)))
                .andExpect(jsonPath("$.familyId").value(targetFamilyId))
                .andExpect(jsonPath("$.title").value("Paella familiar"))
                .andReturn();
        String copiedRecipeId = read(copied, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", targetFamilyId, copiedRecipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Arroz"))
                .andExpect(jsonPath("$[1].name").value("Caldo"));
        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/steps", targetFamilyId, copiedRecipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].instruction").value("Cocer el arroz"));
    }

    @Test
    void copyRecipeRequiresSourceMembershipAndTargetWriteAccess() throws Exception {
        String sourceOwnerEmail = uniqueEmail("recipes-copy-source");
        RegisteredUser sourceOwner = register(sourceOwnerEmail, "Familia Fuente");
        RegisteredUser targetOwner = register(uniqueEmail("recipes-copy-target"), "Familia Destino Protegida");
        MvcResult source = createRecipe(sourceOwner, "Receta Fuente").andReturn();
        String recipeId = read(source, "id");
        addFamilyMember(targetOwner.familyId(), sourceOwnerEmail, FamilyRole.MEMBER);

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/copy",
                        sourceOwner.familyId(),
                        recipeId)
                        .header("Authorization", "Bearer " + targetOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetFamilyId": "%s"}
                                """.formatted(targetOwner.familyId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/copy",
                        sourceOwner.familyId(),
                        recipeId)
                        .header("Authorization", "Bearer " + sourceOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetFamilyId": "%s"}
                                """.formatted(targetOwner.familyId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request_error"));
    }

    @Test
    void replacesAndListsRecipeIngredientsInOrder() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipe-ingredients"), "Familia Ingredientes");
        MvcResult created = createRecipe(user, "Croquetas familiares").andReturn();
        String recipeId = read(created, "id");

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "name": "Leche",
                                      "quantity": 500,
                                      "unit": "ml",
                                      "note": "Entera"
                                    },
                                    {
                                      "name": "Harina",
                                      "quantity": 80,
                                      "unit": "g"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].name").value("Leche"))
                .andExpect(jsonPath("$[0].deleted").value(false))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].name").value("Harina"));

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "name": "Jamon",
                                      "quantity": 120,
                                      "unit": "g"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].name").value("Jamon"));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Jamon"));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients?includeDeleted=true",
                        user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.name == 'Leche' && @.deleted == true)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Harina' && @.deleted == true)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Jamon' && @.deleted == false)]").exists());
    }

    @Test
    void replacesAndListsRecipeStepsInOrder() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipe-steps"), "Familia Pasos");
        MvcResult created = createRecipe(user, "Bizcocho familiar").andReturn();
        String recipeId = read(created, "id");

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/steps", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "instruction": "Mezclar los ingredientes secos",
                                      "timerMinutes": 0
                                    },
                                    {
                                      "instruction": "Hornear hasta que quede dorado",
                                      "timerMinutes": 35
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].instruction").value("Mezclar los ingredientes secos"))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].timerMinutes").value(35));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/steps", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].instruction").value("Hornear hasta que quede dorado"));
    }

    @Test
    void softDeletesRecipeContentWhenDeletingRecipe() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipe-delete-content"), "Familia Borrado Contenido");
        MvcResult created = createRecipe(user, "Sopa familiar").andReturn();
        String recipeId = read(created, "id");

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "name": "Caldo",
                                      "quantity": 1,
                                      "unit": "l"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/steps", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "instruction": "Calentar el caldo",
                                      "timerMinutes": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        List<RecipeIngredientEntity> ingredients = ingredientRepository.findByRecipe_IdOrderByPositionAsc(recipeId);
        List<RecipeStepEntity> steps = stepRepository.findByRecipe_IdOrderByPositionAsc(recipeId);

        org.assertj.core.api.Assertions.assertThat(ingredients)
                .hasSize(1)
                .allMatch(RecipeIngredientEntity::isDeleted);
        org.assertj.core.api.Assertions.assertThat(steps)
                .hasSize(1)
                .allMatch(RecipeStepEntity::isDeleted);
    }

    @Test
    void blocksRecipeContentAccessToAnotherFamily() throws Exception {
        RegisteredUser first = register(uniqueEmail("recipe-content-one"), "Familia Contenido Uno");
        RegisteredUser second = register(uniqueEmail("recipe-content-two"), "Familia Contenido Dos");
        MvcResult secondRecipe = createRecipe(second, "Receta familiar privada").andReturn();
        String secondRecipeId = read(secondRecipe, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients",
                        second.familyId(), secondRecipeId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/steps",
                        second.familyId(), secondRecipeId)
                        .header("Authorization", "Bearer " + first.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "instruction": "Intento ajeno"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesRecipeContentInput() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipe-content-validation"), "Familia Contenido Validacion");
        MvcResult created = createRecipe(user, "Arroz familiar").andReturn();
        String recipeId = read(created, "id");

        mockMvc.perform(put("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "name": "",
                                      "quantity": -1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void listAndDetailExposeCoverFromLowestPositionPhoto() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-cover"), "Familia Portada");
        MvcResult created = createRecipe(user, "Receta con portada").andReturn();
        String recipeId = read(created, "id");

        // Se crean en orden inverso a proposito: la portada es la de position menor,
        // no la primera insertada.
        addPhotoMetadata(user, recipeId, 2, "https://cdn.test/segunda.jpg", "https://cdn.test/segunda-thumb.jpg");
        addPhotoMetadata(user, recipeId, 1, "https://cdn.test/primera.jpg", "https://cdn.test/primera-thumb.jpg");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].coverThumbnailUrl").value("https://cdn.test/primera-thumb.jpg"));

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverThumbnailUrl").value("https://cdn.test/primera-thumb.jpg"));
    }

    @Test
    void coverFallsBackToFullUrlAndIsNullWithoutPhotos() throws Exception {
        RegisteredUser user = register(uniqueEmail("recipes-cover-fallback"), "Familia Fallback");

        MvcResult withoutPhotos = createRecipe(user, "Receta sin fotos").andReturn();
        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}",
                        user.familyId(), read(withoutPhotos, "id"))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverThumbnailUrl").doesNotExist());

        MvcResult withoutThumb = createRecipe(user, "Receta sin thumbnail").andReturn();
        String recipeId = read(withoutThumb, "id");
        addPhotoMetadata(user, recipeId, 1, "https://cdn.test/solo-original.jpg", null);

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes/{recipeId}", user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverThumbnailUrl").value("https://cdn.test/solo-original.jpg"));
    }

    @Test
    void coverIsNotLeakedAcrossFamilies() throws Exception {
        RegisteredUser owner = register(uniqueEmail("cover-owner"), "Familia Duena");
        RegisteredUser outsider = register(uniqueEmail("cover-outsider"), "Familia Ajena");

        MvcResult created = createRecipe(owner, "Receta privada").andReturn();
        String recipeId = read(created, "id");
        addPhotoMetadata(owner, recipeId, 1, "https://cdn.test/privada.jpg", "https://cdn.test/privada-thumb.jpg");

        mockMvc.perform(get("/api/v1/families/{familyId}/recipes", outsider.familyId())
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private void addPhotoMetadata(
            RegisteredUser user,
            String recipeId,
            int position,
            String url,
            String thumbnailUrl
    ) throws Exception {
        String thumbnailJson = thumbnailUrl == null ? "null" : "\"" + thumbnailUrl + "\"";
        mockMvc.perform(post("/api/v1/families/{familyId}/recipes/{recipeId}/photos",
                        user.familyId(), recipeId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"position": %d, "url": "%s", "thumbnailUrl": %s}
                                """.formatted(position, url, thumbnailJson)))
                .andExpect(status().isCreated());
    }

    private RegisteredUser register(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Recipe User",
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

    private void addFamilyMember(String familyId, String email, FamilyRole role) {
        FamilyEntity family = familyRepository.findById(familyId).orElseThrow();
        UserEntity user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email).orElseThrow();
        familyMemberRepository.save(new FamilyMemberEntity(family, user, role));
    }

    private org.springframework.test.web.servlet.ResultActions createRecipe(RegisteredUser user, String title) throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/recipes", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Receta base de casa",
                                  "servings": 4,
                                  "prepMinutes": 10,
                                  "cookMinutes": 20,
                                  "difficulty": "EASY"
                                }
                """.formatted(title)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions createFamily(RegisteredUser user, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/families")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name)))
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
