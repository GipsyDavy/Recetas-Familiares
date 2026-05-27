package org.gipsybuho.recetasfamiliares.menus;

import java.time.Instant;
import java.time.LocalDate;

public record MenuItemResponse(
        String id,
        String familyId,
        String recipeId,
        String recipeTitle,
        LocalDate plannedDate,
        MenuMealType mealType,
        String note,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
