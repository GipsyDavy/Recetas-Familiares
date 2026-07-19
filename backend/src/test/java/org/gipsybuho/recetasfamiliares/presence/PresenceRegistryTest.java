package org.gipsybuho.recetasfamiliares.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PresenceRegistryTest {

    private final PresenceRegistry registry = new PresenceRegistry();

    @Test
    void firstSubscriptionMarksUserOnline() {
        boolean becameOnline = registry.subscribe("session-1", "family-1", "user-a");

        assertThat(becameOnline).isTrue();
        assertThat(registry.onlineUserIds("family-1")).containsExactly("user-a");
    }

    @Test
    void secondSessionSameUserDoesNotDuplicateOrResetState() {
        registry.subscribe("session-1", "family-1", "user-a");
        boolean becameOnlineAgain = registry.subscribe("session-2", "family-1", "user-a");

        assertThat(becameOnlineAgain).isFalse();
        assertThat(registry.onlineUserIds("family-1")).containsExactly("user-a");
    }

    @Test
    void userStaysOnlineWhileAnyOtherSessionOpen() {
        registry.subscribe("session-1", "family-1", "user-a");
        registry.subscribe("session-2", "family-1", "user-a");

        Set<String> changed = registry.unsubscribeSession("session-1");

        assertThat(changed).isEmpty();
        assertThat(registry.onlineUserIds("family-1")).containsExactly("user-a");
    }

    @Test
    void disconnectingLastSessionRemovesUserFromFamily() {
        registry.subscribe("session-1", "family-1", "user-a");

        Set<String> changed = registry.unsubscribeSession("session-1");

        assertThat(changed).containsExactly("family-1");
        assertThat(registry.onlineUserIds("family-1")).isEmpty();
    }

    @Test
    void disconnectingUnknownSessionIsNoop() {
        Set<String> changed = registry.unsubscribeSession("never-subscribed");

        assertThat(changed).isEmpty();
    }

    @Test
    void tracksMultipleFamiliesIndependently() {
        registry.subscribe("session-1", "family-1", "user-a");
        registry.subscribe("session-1", "family-2", "user-a");

        Set<String> changed = registry.unsubscribeSession("session-1");

        assertThat(changed).containsExactlyInAnyOrder("family-1", "family-2");
        assertThat(registry.onlineUserIds("family-1")).isEmpty();
        assertThat(registry.onlineUserIds("family-2")).isEmpty();
    }
}
