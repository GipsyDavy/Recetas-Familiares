package org.gipsybuho.recetasfamiliares.activity;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Avisa en vivo que una seccion de la familia tiene actividad nueva. Sin contenido del cambio. */
@Component
public class FamilyActivityRealtimePublisher {

    static final String TOPIC_PREFIX = "/topic/families/";
    static final String TOPIC_SUFFIX = "/activity";

    private final SimpMessagingTemplate messagingTemplate;

    public FamilyActivityRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    static String topicFor(String familyId) {
        return TOPIC_PREFIX + familyId + TOPIC_SUFFIX;
    }

    public void publish(String familyId, FamilySection section) {
        messagingTemplate.convertAndSend(topicFor(familyId), new FamilyActivityPing(section));
    }
}
