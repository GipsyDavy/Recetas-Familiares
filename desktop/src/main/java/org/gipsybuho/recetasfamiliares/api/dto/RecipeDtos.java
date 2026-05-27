package org.gipsybuho.recetasfamiliares.api.dto;

import java.util.List;

public final class RecipeDtos {

    private RecipeDtos() {}

    public record RecipeDto(
            String id,
            String familyId,
            String title,
            String description,
            Integer servings,
            Integer prepMinutes,
            Integer cookMinutes,
            String difficulty,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RecipeIngredientDto(
            String id,
            String recipeId,
            String name,
            String quantity,
            String unit,
            Integer sortOrder,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RecipeStepDto(
            String id,
            String recipeId,
            Integer stepNumber,
            String description,
            Integer durationMinutes,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RecipePageResponse(
            List<RecipeDto> content,
            int totalElements,
            int totalPages,
            int number
    ) {}
}
