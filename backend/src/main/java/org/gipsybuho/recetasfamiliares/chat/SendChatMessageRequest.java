package org.gipsybuho.recetasfamiliares.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Envio de mensaje de texto (fase 1). El cliente puede generar el {@code id}
 * (UUID v4) para idempotencia: reenviar el mismo id no duplica.
 */
public record SendChatMessageRequest(
        @Size(max = 36)
        String id,

        @NotBlank
        @Size(max = 2000)
        String body
) {
}
