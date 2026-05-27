package org.gipsybuho.recetasfamiliares.favorites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFavoriteRecipeRequest(
        @NotBlank
        @Size(max = 36)
        String recipeId
) {
}
