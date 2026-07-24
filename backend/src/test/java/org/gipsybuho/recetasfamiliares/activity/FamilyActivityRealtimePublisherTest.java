package org.gipsybuho.recetasfamiliares.activity;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class FamilyActivityRealtimePublisherTest {

    @Test
    void publishesSectionOnlyNoContent() {
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        FamilyActivityRealtimePublisher publisher = new FamilyActivityRealtimePublisher(messagingTemplate);

        publisher.publish("fam-1", FamilySection.RECIPE);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/families/fam-1/activity"),
                eq(new FamilyActivityPing(FamilySection.RECIPE)));
    }
}
