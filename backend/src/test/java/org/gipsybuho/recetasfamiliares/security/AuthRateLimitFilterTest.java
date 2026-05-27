package org.gipsybuho.recetasfamiliares.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.security.rate-limit.auth.enabled=true",
        "app.security.rate-limit.auth.max-requests=2",
        "app.security.rate-limit.auth.window-seconds=60"
})
@AutoConfigureMockMvc
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void limitsRepeatedAuthenticationRequestsByClientAndEndpoint() throws Exception {
        performLoginAttempt().andExpect(status().isUnauthorized());
        performLoginAttempt().andExpect(status().isUnauthorized());

        performLoginAttempt()
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("rate_limited"))
                .andExpect(jsonPath("$.message").value("Too many authentication requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    private org.springframework.test.web.servlet.ResultActions performLoginAttempt() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "rate-limit@example.com",
                          "password": "wrong-password"
                        }
                        """));
    }
}
