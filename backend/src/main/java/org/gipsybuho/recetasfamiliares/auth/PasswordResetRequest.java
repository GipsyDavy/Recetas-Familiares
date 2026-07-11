package org.gipsybuho.recetasfamiliares.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @Email @NotBlank @Size(max = 254) String email
) {
}
