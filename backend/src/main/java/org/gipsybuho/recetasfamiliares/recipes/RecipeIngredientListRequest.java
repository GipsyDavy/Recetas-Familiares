package org.gipsybuho.recetasfamiliares.recipes;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RecipeIngredientListRequest(
        @NotNull
        List<@NotNull @Valid RecipeIngredientRequest> items
) {
}
