package org.gipsybuho.recetasfamiliares.stock;

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
class StockItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsUpdatesAndSoftDeletesStockItem() throws Exception {
        RegisteredUser user = register("stock-owner@example.com", "Familia Stock");
        MvcResult created = createStockItem(user, "Arroz").andReturn();
        String stockItemId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/stock-items", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(stockItemId))
                .andExpect(jsonPath("$.items[0].name").value("Arroz"));

        mockMvc.perform(put("/api/v1/families/{familyId}/stock-items/{stockItemId}", user.familyId(), stockItemId)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Arroz bomba",
                                  "quantity": 1.500,
                                  "unit": "kg",
                                  "lowStockThreshold": 0.500,
                                  "expiresAt": "2026-12-31",
                                  "note": "Para paella"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Arroz bomba"))
                .andExpect(jsonPath("$.quantity").value(1.5))
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.syncVersion", greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/v1/families/{familyId}/stock-items/{stockItemId}", user.familyId(), stockItemId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/families/{familyId}/stock-items/{stockItemId}", user.familyId(), stockItemId)
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksStockAccessToAnotherFamily() throws Exception {
        RegisteredUser first = register("stock-one@example.com", "Familia Stock Uno");
        RegisteredUser second = register("stock-two@example.com", "Familia Stock Dos");
        MvcResult created = createStockItem(second, "Aceite").andReturn();
        String stockItemId = read(created, "id");

        mockMvc.perform(get("/api/v1/families/{familyId}/stock-items", second.familyId())
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/families/{familyId}/stock-items/{stockItemId}", second.familyId(), stockItemId)
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesStockInput() throws Exception {
        RegisteredUser user = register("stock-validation@example.com", "Familia Stock Validacion");

        mockMvc.perform(post("/api/v1/families/{familyId}/stock-items", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "quantity": -1
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
                                  "displayName": "Stock User",
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

    private org.springframework.test.web.servlet.ResultActions createStockItem(RegisteredUser user, String name)
            throws Exception {
        return mockMvc.perform(post("/api/v1/families/{familyId}/stock-items", user.familyId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "quantity": 2,
                                  "unit": "kg",
                                  "lowStockThreshold": 1,
                                  "expiresAt": "2026-12-31",
                                  "note": "Despensa"
                                }
                                """.formatted(name)))
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
