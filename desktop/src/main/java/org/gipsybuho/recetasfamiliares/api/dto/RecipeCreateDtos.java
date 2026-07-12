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
            String note
    ) {}

    public record CreateStepRequest(
            String instruction,
            Integer timerMinutes
    ) {}

    public record UpdateRecipeRequest(
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            String difficulty
    ) {}

    public record ReplaceIngredientsRequest(List<CreateIngredientRequest> items) {}

    public record ReplaceStepsRequest(List<CreateStepRequest> items) {}

    public record CopyRecipeRequest(String targetFamilyId) {}
}
