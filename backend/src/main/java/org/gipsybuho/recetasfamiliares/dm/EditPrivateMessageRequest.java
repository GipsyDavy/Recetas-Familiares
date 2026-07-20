package org.gipsybuho.recetasfamiliares.dm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditPrivateMessageRequest(
        @NotBlank
        @Size(max = 2000)
        String body
) {
}
