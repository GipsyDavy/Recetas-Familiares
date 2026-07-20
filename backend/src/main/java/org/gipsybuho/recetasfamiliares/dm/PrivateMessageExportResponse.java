package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;
import java.util.List;

public record PrivateMessageExportResponse(
        String conversationId,
        Instant exportedAt,
        int totalMessages,
        List<PrivateMessageResponse> messages
) {
}
