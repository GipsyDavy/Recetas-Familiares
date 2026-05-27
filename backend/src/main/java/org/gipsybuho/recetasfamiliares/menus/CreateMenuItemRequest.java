package org.gipsybuho.recetasfamiliares.menus;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMenuItemRequest(
        @Size(max = 36)
        String recipeId,

        @NotNull
        LocalDate plannedDate,

        @NotNull
        MenuMealType mealType,

        @Size(max = 255)
        String note
) {
}
