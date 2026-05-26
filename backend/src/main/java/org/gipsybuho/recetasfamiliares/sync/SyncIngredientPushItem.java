package org.gipsybuho.recetasfamiliares.sync;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SyncIngredientPushItem(
        @NotBlank
        @Size(max = 36)
        String id,

        @NotBlank
        @Size(max = 36)
        String recipeId,

        @Min(1)
        int position,

        @Size(max = 180)
        String name,

        @DecimalMin("0.001")
        BigDecimal quantity,

        @Size(max = 40)
        String unit,

        @Size(max = 255)
        String note,

        boolean deleted
) {
}
