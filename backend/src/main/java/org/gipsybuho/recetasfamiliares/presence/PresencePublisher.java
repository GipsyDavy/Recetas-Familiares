package org.gipsybuho.recetasfamiliares.presence;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Difunde el snapshot completo de presencia de una familia (no deltas: evita
 * bugs de eventos perdidos si un cliente se suscribe tarde).
 */
@Component
public class PresencePublisher {

    static final String TOPIC_PREFIX = "/topic/families/";
    static final String TOPIC_SUFFIX = "/presence";

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceRegistry registry;

    public PresencePublisher(SimpMessagingTemplate messagingTemplate, PresenceRegistry registry) {
        this.messagingTemplate = messagingTemplate;
        this.registry = registry;
    }

    static String topicFor(String familyId) {
        return TOPIC_PREFIX + familyId + TOPIC_SUFFIX;
    }

    public void publish(String familyId) {
        messagingTemplate.convertAndSend(topicFor(familyId), new PresenceResponse(registry.onlineUserIds(familyId)));
    }
}
