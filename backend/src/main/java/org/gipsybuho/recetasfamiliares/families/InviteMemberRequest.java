package org.gipsybuho.recetasfamiliares.families;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(
        @Email @NotBlank String email,
        @Size(max = 120) String displayName,
        @Size(min = 12, max = 128) String password,
        @NotBlank String role
) {
}
