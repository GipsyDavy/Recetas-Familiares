package org.gipsybuho.recetasfamiliares.presence;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PresencePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PresenceRegistry registry;

    private PresencePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PresencePublisher(messagingTemplate, registry);
    }

    @Test
    void publishesCurrentSnapshotToFamilyPresenceTopic() {
        when(registry.onlineUserIds("family-1")).thenReturn(List.of("user-a", "user-b"));

        publisher.publish("family-1");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/families/family-1/presence"),
                eq(new PresenceResponse(List.of("user-a", "user-b"))));
    }
}
