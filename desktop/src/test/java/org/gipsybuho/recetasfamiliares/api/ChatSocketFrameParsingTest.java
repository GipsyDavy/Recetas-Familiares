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
}
