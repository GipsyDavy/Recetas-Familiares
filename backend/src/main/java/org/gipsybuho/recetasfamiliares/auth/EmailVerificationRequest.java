package org.gipsybuho.recetasfamiliares.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(
        @Email @NotBlank @Size(max = 254) String email
) {
}
