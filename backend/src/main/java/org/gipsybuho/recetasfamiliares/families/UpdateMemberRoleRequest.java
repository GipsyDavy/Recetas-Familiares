package org.gipsybuho.recetasfamiliares.families;

import jakarta.validation.constraints.NotBlank;

public record UpdateMemberRoleRequest(
        @NotBlank String role
) {
}
