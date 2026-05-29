package org.gipsybuho.recetasfamiliares.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String displayName
) {
}
