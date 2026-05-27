package org.gipsybuho.recetasfamiliares.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.gipsybuho.recetasfamiliares.common.api.ApiError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;
    private final int maxRequests;
    private final Duration window;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuthRateLimitFilter(
            @Value("${app.security.rate-limit.auth.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.auth.max-requests:20}") int maxRequests,
            @Value("${app.security.rate-limit.auth.window-seconds:60}") long windowSeconds,
            ObjectMapper objectMapper
    ) {
        this(Clock.systemUTC(), enabled, maxRequests, Duration.ofSeconds(windowSeconds), objectMapper);
    }

    AuthRateLimitFilter(Clock clock, boolean enabled, int maxRequests, Duration window, ObjectMapper objectMapper) {
        this.clock = clock;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.window = window;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!shouldLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant now = clock.instant();
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        AttemptWindow attemptWindow = attempts.compute(key, (ignored, current) -> nextWindow(current, now));
        cleanupExpiredWindows(now);

        if (attemptWindow.count() > maxRequests) {
            ApiError apiError = ApiError.of(
                    "rate_limited",
                    "Too many authentication requests",
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    request.getRequestURI()
            );
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
            objectMapper.writeValue(response.getOutputStream(), apiError);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldLimit(HttpServletRequest request) {
        return enabled
                && maxRequests > 0
                && HttpMethod.POST.matches(request.getMethod())
                && LIMITED_PATHS.contains(request.getRequestURI());
    }

    private AttemptWindow nextWindow(AttemptWindow current, Instant now) {
        if (current == null || !current.expiresAt().isAfter(now)) {
            return new AttemptWindow(1, now.plus(window));
        }
        return new AttemptWindow(current.count() + 1, current.expiresAt());
    }

    private void cleanupExpiredWindows(Instant now) {
        attempts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record AttemptWindow(int count, Instant expiresAt) {
    }
}
