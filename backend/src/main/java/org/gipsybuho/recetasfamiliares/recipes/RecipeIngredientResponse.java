package org.gipsybuho.recetasfamiliares.recipes;

import java.math.BigDecimal;
import java.time.Instant;

public record RecipeIngredientResponse(
        String id,
        String recipeId,
        int position,
        String name,
        BigDecimal quantity,
        String unit,
        String note,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
