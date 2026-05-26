package org.gipsybuho.recetasfamiliares.recipes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipeStepRequest(
        @NotBlank
        @Size(max = 2000)
        String instruction,

        @Min(0)
        Integer timerMinutes
) {
}
