package org.gipsybuho.recetasfamiliares.common.config;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsBearerSecurityOnlyForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("bearerAuth")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset/request'].post.security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/auth/account'].delete.security[0]", hasKey("bearerAuth")))
                .andExpect(jsonPath("$.paths['/api/v1/families'].get.security[0]", hasKey("bearerAuth")));
    }

    @Test
    void documentsMvpApiPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/register")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/refresh")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/logout")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/password-reset/request")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/password-reset/confirm")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/email-verification/request")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/email-verification/confirm")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/account")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/health")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/recipes")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/recipes/{recipeId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/recipes/{recipeId}/ingredients")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/recipes/{recipeId}/steps")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/stock-items")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/stock-items/{stockItemId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/menu-items")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/menu-items/{menuItemId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/shopping-lists")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/shopping-lists/generate-from-menu")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/favorite-recipes")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/favorite-recipes/{favoriteId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/notes")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/notes/{noteId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/recipes/{recipeId}/photos")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/recipes/{recipeId}/photos/{photoId}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/sync/pull")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/families/{familyId}/sync/push")));
    }
}
