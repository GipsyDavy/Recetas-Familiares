package org.gipsybuho.recetasfamiliares.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(
        @NotBlank @Size(min = 32, max = 256) String token
) {
}
