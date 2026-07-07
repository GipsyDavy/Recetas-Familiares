package org.gipsybuho.recetasfamiliares.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica mensajes nuevos en el topic STOMP de la familia para entrega en tiempo
 * real. El cliente envia por REST (POST) y recibe por WebSocket suscrito a
 * {@code /topic/families/{familyId}/chat}.
 */
@Component
public class ChatRealtimePublisher {

    static final String TOPIC_PREFIX = "/topic/families/";
    static final String TOPIC_SUFFIX = "/chat";

    private final SimpMessagingTemplate messagingTemplate;

    public ChatRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    static String topicFor(String familyId) {
        return TOPIC_PREFIX + familyId + TOPIC_SUFFIX;
    }

    void publishMessage(ChatMessageResponse message) {
        messagingTemplate.convertAndSend(topicFor(message.familyId()), message);
    }
}
