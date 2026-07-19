package org.gipsybuho.recetasfamiliares.presence;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@ExtendWith(MockitoExtension.class)
class PresenceDisconnectListenerTest {

    @Mock
    private PresenceRegistry registry;
    @Mock
    private PresencePublisher publisher;

    private PresenceDisconnectListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceDisconnectListener(registry, publisher);
    }

    @Test
    void cleansUpRegistryAndPublishesForEachAffectedFamily() {
        when(registry.unsubscribeSession("session-1")).thenReturn(Set.of("family-1", "family-2"));
        SessionDisconnectEvent event = disconnectEvent("session-1");

        listener.onSessionDisconnect(event);

        verify(publisher).publish("family-1");
        verify(publisher).publish("family-2");
    }

    @Test
    void noPublishWhenSessionHadNoPresenceSubscriptions() {
        when(registry.unsubscribeSession("session-2")).thenReturn(Set.of());
        SessionDisconnectEvent event = disconnectEvent("session-2");

        listener.onSessionDisconnect(event);

        verifyNoInteractions(publisher);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }
}
