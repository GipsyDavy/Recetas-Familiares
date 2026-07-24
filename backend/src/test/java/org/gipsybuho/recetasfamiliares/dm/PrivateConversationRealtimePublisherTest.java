package org.gipsybuho.recetasfamiliares.dm;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PrivateConversationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private PrivateConversationRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PrivateConversationRealtimePublisher(messagingTemplate);
    }

    @Test
    void publishesMessageToConversationTopic() {
        PrivateMessageResponse message = new PrivateMessageResponse(
                "msg-1", "conv-1", "author-1", "Author", "hola",
                List.of(), Instant.EPOCH, Instant.EPOCH, 0L, false);

        publisher.publishNewMessage(message, "recipient-1");

        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-1"), eq(message));
    }

    @Test
    void publishesInboxPingToRecipientOnlyWithoutMessageBody() {
        PrivateMessageResponse message = new PrivateMessageResponse(
                "msg-2", "conv-2", "author-2", "Author", "contenido secreto",
                List.of(), Instant.EPOCH, Instant.EPOCH, 0L, false);

        publisher.publishNewMessage(message, "recipient-2");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/users/recipient-2/inbox"),
                eq(new PrivateInboxPing("conv-2", "author-2", Instant.EPOCH)));
    }

    /**
     * Regresion: editar/borrar un mensaje ya enviado no debe generar un nuevo
     * ping de bandeja (el receptor ya conto ese mensaje como no-leido cuando
     * se envio la primera vez) — solo debe actualizar el topic de la
     * conversacion, para quien la tenga abierta en tiempo real.
     */
    @Test
    void publishUpdateOnlyNotifiesConversationTopicNotInbox() {
        PrivateMessageResponse message = new PrivateMessageResponse(
                "msg-3", "conv-3", "author-3", "Author", "editado",
                List.of(), Instant.EPOCH, Instant.EPOCH, 1L, false);

        publisher.publishUpdate(message);

        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/conv-3"), eq(message));
        verifyNoMoreInteractions(messagingTemplate);
    }
}
