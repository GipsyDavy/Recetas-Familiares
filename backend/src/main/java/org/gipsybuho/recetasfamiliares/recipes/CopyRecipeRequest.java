package org.gipsybuho.recetasfamiliares.recipes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CopyRecipeRequest(
        @NotBlank
        @Size(max = 36)
        String targetFamilyId
) {
}
