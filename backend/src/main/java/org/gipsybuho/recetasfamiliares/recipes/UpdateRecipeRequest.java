package org.gipsybuho.recetasfamiliares.recipes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRecipeRequest(
        @NotBlank @Size(max = 180) String title,
        @Size(max = 1000) String description,
        @Min(1) @Max(100) Integer servings,
        @Min(0) @Max(4320) Integer prepMinutes,
        @Min(0) @Max(4320) Integer cookMinutes,
        RecipeDifficulty difficulty
) {
}
