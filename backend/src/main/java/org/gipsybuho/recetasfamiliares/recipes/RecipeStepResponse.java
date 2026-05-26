package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;

public record RecipeStepResponse(
        String id,
        String recipeId,
        int position,
        String instruction,
        Integer timerMinutes,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
