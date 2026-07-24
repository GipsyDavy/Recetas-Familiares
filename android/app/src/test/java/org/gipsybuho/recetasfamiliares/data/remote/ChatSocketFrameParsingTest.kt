package org.gipsybuho.recetasfamiliares.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatSocketFrameParsingTest {

    @Test
    fun extractsHeaderValueFromFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/families/fam-1/presence\n" +
            "subscription:sub-presence\n" +
            "\n" +
            "{\"onlineUserIds\":[\"user-a\"]}"

        assertEquals("/topic/families/fam-1/presence", extractStompHeader(frame, "destination"))
    }

    @Test
    fun returnsNullWhenHeaderMissing() {
        val frame = "MESSAGE\n" +
            "subscription:sub-chat\n" +
            "\n" +
            "{}"

        assertNull(extractStompHeader(frame, "destination"))
    }

    @Test
    fun ignoresHeaderNameAppearingOnlyInBody() {
        val frame = "MESSAGE\n" +
            "subscription:sub-chat\n" +
            "\n" +
            "{\"destination\":\"not-a-header\"}"

        assertNull(extractStompHeader(frame, "destination"))
    }

    @Test
    fun extractsDestinationFromInboxFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/users/u1/inbox\n" +
            "subscription:sub-inbox\n" +
            "\n" +
            "{\"conversationId\":\"c1\",\"senderUserId\":\"u2\",\"sentAt\":\"2026-07-23T10:00:00Z\"}"

        assertEquals("/topic/users/u1/inbox", extractStompHeader(frame, "destination"))
    }

    @Test
    fun extractsDestinationFromConversationFrame() {
        val frame = "MESSAGE\n" +
            "destination:/topic/conversations/c1\n" +
            "subscription:sub-conversation\n" +
            "\n" +
            "{\"id\":\"m1\"}"

        assertEquals("/topic/conversations/c1", extractStompHeader(frame, "destination"))
    }
}
