package org.gipsybuho.recetasfamiliares.favorites;

import java.time.Instant;

public record FavoriteRecipeResponse(
        String id,
        String familyId,
        String recipeId,
        String recipeTitle,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
