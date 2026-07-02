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

    @Test
    void spoofedForwardedForHeaderDoesNotBypassLimitWhenProxyNotTrusted() throws Exception {
        // remoteAddr propia para no compartir ventana con el otro test (misma instancia de filtro)
        performLoginAttempt("10.99.0.1", "1.1.1.1").andExpect(status().isUnauthorized());
        performLoginAttempt("10.99.0.1", "2.2.2.2").andExpect(status().isUnauthorized());

        performLoginAttempt("10.99.0.1", "3.3.3.3").andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions performLoginAttempt() throws Exception {
        return performLoginAttempt(null, null);
    }

    private org.springframework.test.web.servlet.ResultActions performLoginAttempt(
            String remoteAddr, String forwardedFor) throws Exception {
        var request = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "rate-limit@example.com",
                          "password": "wrong-password"
                        }
                        """);
        if (forwardedFor != null) {
            request = request.header("X-Forwarded-For", forwardedFor);
        }
        if (remoteAddr != null) {
            request = request.with(mockRequest -> {
                mockRequest.setRemoteAddr(remoteAddr);
                return mockRequest;
            });
        }
        return mockMvc.perform(request);
    }
}
