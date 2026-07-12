package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;

public record RecipeResponse(
        String id,
        String familyId,
        String title,
        String description,
        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        RecipeDifficulty difficulty,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted,
        String createdByUserId,
        String createdByDisplayName
) {
}
