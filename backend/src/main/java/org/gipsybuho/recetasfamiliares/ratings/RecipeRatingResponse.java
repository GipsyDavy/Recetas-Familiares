package org.gipsybuho.recetasfamiliares.ratings;

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
