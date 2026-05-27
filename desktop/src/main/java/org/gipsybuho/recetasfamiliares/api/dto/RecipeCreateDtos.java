package org.gipsybuho.recetasfamiliares.api.dto;

import java.util.List;

public final class RecipeCreateDtos {

    private RecipeCreateDtos() {}

    public record CreateRecipeRequest(
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            String difficulty
    ) {}

    public record CreateIngredientRequest(
            String name,
            String quantity,
            String unit,
            int sortOrder
    ) {}

    public record CreateStepRequest(
            int stepNumber,
            String description,
            Integer durationMinutes
    ) {}

    public record UpdateRecipeRequest(
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            String difficulty
    ) {}

    public record ReplaceIngredientsRequest(List<CreateIngredientRequest> ingredients) {}

    public record ReplaceStepsRequest(List<CreateStepRequest> steps) {}
}
