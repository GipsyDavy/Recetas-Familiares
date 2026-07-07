package org.gipsybuho.recetasfamiliares.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limit de envio de mensajes por usuario para evitar spam accidental o
 * abuso. Ventana deslizante en memoria (suficiente para 1 familia / servidor
 * domestico). No sustituye el rate limit de auth; es especifico del chat.
 */
@Component
public class ChatSendRateLimiter {

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;
    private final int maxMessages;
    private final Duration window;

    @Autowired
    public ChatSendRateLimiter(
            @Value("${app.security.rate-limit.chat.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.chat.max-messages:10}") int maxMessages,
            @Value("${app.security.rate-limit.chat.window-seconds:10}") long windowSeconds
    ) {
        this(Clock.systemUTC(), enabled, maxMessages, Duration.ofSeconds(windowSeconds));
    }

    ChatSendRateLimiter(Clock clock, boolean enabled, int maxMessages, Duration window) {
        this.clock = clock;
        this.enabled = enabled;
        this.maxMessages = maxMessages;
        this.window = window;
    }

    /**
     * @return true si el envio esta permitido; false si supera el limite.
     */
    public boolean tryAcquire(String userId) {
        if (!enabled || maxMessages <= 0) {
            return true;
        }
        Instant now = clock.instant();
        AttemptWindow current = attempts.compute(userId, (ignored, existing) -> nextWindow(existing, now));
        cleanupExpired(now);
        return current.count() <= maxMessages;
    }

    private AttemptWindow nextWindow(AttemptWindow current, Instant now) {
        if (current == null || !current.expiresAt().isAfter(now)) {
            return new AttemptWindow(1, now.plus(window));
        }
        return new AttemptWindow(current.count() + 1, current.expiresAt());
    }

    private void cleanupExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record AttemptWindow(int count, Instant expiresAt) {
    }
}
