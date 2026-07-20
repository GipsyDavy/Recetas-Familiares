package org.gipsybuho.recetasfamiliares.dm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Envio de mensaje de texto. El cliente puede generar el {@code id} (UUID v4)
 * para idempotencia: reenviar el mismo id no duplica.
 */
public record SendPrivateMessageRequest(
        @Size(max = 36)
        String id,

        @NotBlank
        @Size(max = 2000)
        String body
) {
}
