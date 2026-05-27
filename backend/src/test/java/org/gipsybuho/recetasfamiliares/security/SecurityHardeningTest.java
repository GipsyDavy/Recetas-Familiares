package org.gipsybuho.recetasfamiliares.security;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.cors.allowed-origins=https://app.recetas.example")
@AutoConfigureMockMvc
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void addsDefensiveSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"
                ))
                .andExpect(header().string(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=()"
                ));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://app.recetas.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.recetas.example"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", not("https://evil.example")));
    }
}
