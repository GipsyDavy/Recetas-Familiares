package org.gipsybuho.recetasfamiliares.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 120) String familyName
) {
}
