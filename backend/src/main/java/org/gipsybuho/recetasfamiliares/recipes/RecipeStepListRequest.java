package org.gipsybuho.recetasfamiliares.recipes;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RecipeStepListRequest(
        @NotNull
        List<@NotNull @Valid RecipeStepRequest> items
) {
}
