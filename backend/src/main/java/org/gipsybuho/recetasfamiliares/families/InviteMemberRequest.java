package org.gipsybuho.recetasfamiliares.families;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteMemberRequest(
        @Email @NotBlank String email,
        @NotBlank String role
) {
}
