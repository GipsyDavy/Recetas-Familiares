package org.gipsybuho.recetasfamiliares.families;

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
class FamilyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsOnlyFamiliesOwnedByAuthenticatedUser() throws Exception {
        String firstAccessToken = registerAndReadAccessToken(
                "familia-uno@example.com",
                "Familia Uno"
        );
        String secondAccessToken = registerAndReadAccessToken(
                "familia-dos@example.com",
                "Familia Dos"
        );

        mockMvc.perform(get("/api/v1/families")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Familia Uno"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));

        mockMvc.perform(get("/api/v1/families")
                        .header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Familia Dos"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    /** COD-4: lastActivityAt debe reflejar actividad de cualquier entidad, no solo recetas. */
    @Test
    void statsLastActivityCoversNonRecipeEntities() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "familia-stats@example.com",
                                  "displayName": "Test User",
                                  "password": "very-secure-password",
                                  "familyName": "Familia Stats"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode auth = objectMapper.readTree(registered.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String token = auth.get("accessToken").asText();
        String familyId = auth.get("family").get("id").asText();

        // Sin actividad: lastActivityAt null
        mockMvc.perform(get("/api/v1/families/{familyId}/stats", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipes").value(0))
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.nullValue()));

        // Una nota (sin recetas) ya cuenta como actividad
        mockMvc.perform(post("/api/v1/families/{familyId}/notes", familyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Nota", "body": "Actividad familiar", "pinned": false}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/families/{familyId}/stats", familyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.notNullValue()));
    }

    private String registerAndReadAccessToken(String email, String familyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "Test User",
                                  "password": "very-secure-password",
                                  "familyName": "%s"
                                }
                                """.formatted(email, familyName)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.get("accessToken").asText();
    }
}
