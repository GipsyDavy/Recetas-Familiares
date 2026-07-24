package org.gipsybuho.recetasfamiliares.dm;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica mensajes privados nuevos por WebSocket en dos topics distintos:
 * el de la conversacion (contenido completo, para quien la tiene abierta) y
 * el de bandeja del destinatario (solo metadata, sin cuerpo del mensaje, para
 * badge/refresco de la lista de conversaciones sin tener que suscribirse a
 * cada conversacion individualmente).
 */
@Component
public class PrivateConversationRealtimePublisher {

    static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";
    static final String INBOX_TOPIC_PREFIX = "/topic/users/";
    static final String INBOX_TOPIC_SUFFIX = "/inbox";

    private final SimpMessagingTemplate messagingTemplate;

    public PrivateConversationRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    static String conversationTopicFor(String conversationId) {
        return CONVERSATION_TOPIC_PREFIX + conversationId;
    }

    static String inboxTopicFor(String userId) {
        return INBOX_TOPIC_PREFIX + userId + INBOX_TOPIC_SUFFIX;
    }

    /** Mensaje nuevo (envio de texto o imagen): notifica la conversacion y hace sonar la bandeja del destinatario. */
    void publishNewMessage(PrivateMessageResponse message, String recipientUserId) {
        messagingTemplate.convertAndSend(conversationTopicFor(message.conversationId()), message);
        messagingTemplate.convertAndSend(
                inboxTopicFor(recipientUserId),
                new PrivateInboxPing(message.conversationId(), message.authorUserId(), message.updatedAt()));
    }

    /**
     * Edicion o borrado de un mensaje ya existente: solo actualiza el topic de la
     * conversacion (para quien la tenga abierta en tiempo real). No genera ping de
     * bandeja — el mensaje ya conto como no-leido cuando se envio la primera vez, y
     * el ping no lleva messageId para poder deduplicar en el cliente.
     */
    void publishUpdate(PrivateMessageResponse message) {
        messagingTemplate.convertAndSend(conversationTopicFor(message.conversationId()), message);
    }
}
