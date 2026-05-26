package org.gipsybuho.recetasfamiliares.recipes;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipeIngredientRequest(
        @NotBlank
        @Size(max = 180)
        String name,

        @DecimalMin("0.001")
        BigDecimal quantity,

        @Size(max = 40)
        String unit,

        @Size(max = 255)
        String note
) {
}
