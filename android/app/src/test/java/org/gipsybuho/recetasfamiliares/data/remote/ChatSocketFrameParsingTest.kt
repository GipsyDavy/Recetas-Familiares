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
}
