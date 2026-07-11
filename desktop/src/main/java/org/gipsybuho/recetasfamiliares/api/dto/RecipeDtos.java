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
            Integer position,
            String name,
            Double quantity,
            String unit,
            String note,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RecipeStepDto(
            String id,
            String recipeId,
            Integer position,
            String instruction,
            Integer timerMinutes,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RecipePageResponse(
            List<RecipeDto> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {}

    public record RecipePhotoResponse(
            String id,
            String recipeId,
            int position,
            String url,
            String thumbnailUrl,
            String caption,
            String contentType,
            Long sizeBytes,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RecipeRatingResponse(
            String id,
            String recipeId,
            String userId,
            String userDisplayName,
            int stars,
            String comment,
            String createdAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record RatingRequest(int stars, String comment) {}
}
