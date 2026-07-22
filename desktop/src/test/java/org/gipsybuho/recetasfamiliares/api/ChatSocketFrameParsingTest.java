package org.gipsybuho.recetasfamiliares.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatSocketFrameParsingTest {

    @Test
    void extractsHeaderValueFromFrame() {
        String frame = "MESSAGE\n"
                + "destination:/topic/families/fam-1/presence\n"
                + "subscription:sub-presence\n"
                + "\n"
                + "{\"onlineUserIds\":[\"user-a\"]}";

        assertEquals("/topic/families/fam-1/presence", ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void returnsNullWhenHeaderMissing() {
        String frame = "MESSAGE\n"
                + "subscription:sub-chat\n"
                + "\n"
                + "{}";

        assertNull(ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void ignoresHeaderNameAppearingOnlyInBody() {
        String frame = "MESSAGE\n"
                + "subscription:sub-chat\n"
                + "\n"
                + "{\"destination\":\"not-a-header\"}";

        assertNull(ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void extractsDestinationFromInboxFrame() {
        String frame = "MESSAGE\n"
                + "destination:/topic/users/u1/inbox\n"
                + "subscription:sub-inbox\n"
                + "\n"
                + "{\"conversationId\":\"c1\",\"senderUserId\":\"u2\",\"sentAt\":\"2026-07-22T10:00:00Z\"}";

        assertEquals("/topic/users/u1/inbox", ChatSocket.extractHeader(frame, "destination"));
    }

    @Test
    void extractsDestinationFromConversationFrame() {
        String frame = "MESSAGE\n"
                + "destination:/topic/conversations/c1\n"
                + "subscription:sub-conversation\n"
                + "\n"
                + "{\"id\":\"m1\"}";

        assertEquals("/topic/conversations/c1", ChatSocket.extractHeader(frame, "destination"));
    }
}
