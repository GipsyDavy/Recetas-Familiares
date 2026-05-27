package org.gipsybuho.recetasfamiliares.shopping;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShoppingListItemRequest(
        @Min(1)
        int position,

        @NotBlank
        @Size(max = 180)
        String name,

        @DecimalMin("0.000")
        BigDecimal quantity,

        @Size(max = 40)
        String unit,

        boolean checked,

        @Size(max = 255)
        String note
) {
}
