package org.gipsybuho.recetasfamiliares.chat;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuracion WebSocket/STOMP para el chat familiar (fase 1).
 * Broker simple embebido (basta para 1 familia / servidor domestico, YAGNI).
 * Solo entrega: los clientes se suscriben a {@code /topic/...} y envian por REST.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatStompAuthChannelInterceptor authChannelInterceptor;
    private final List<String> allowedOriginPatterns;

    public WebSocketConfig(
            ChatStompAuthChannelInterceptor authChannelInterceptor,
            @Value("${app.websocket.allowed-origin-patterns:*}") String allowedOriginPatterns
    ) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.allowedOriginPatterns = parseCsv(allowedOriginPatterns);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of("*");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }
}
