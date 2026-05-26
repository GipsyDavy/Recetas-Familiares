package org.gipsybuho.recetasfamiliares.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStockItemRequest(
        @NotBlank
        @Size(max = 180)
        String name,

        @DecimalMin("0.000")
        BigDecimal quantity,

        @Size(max = 40)
        String unit,

        @DecimalMin("0.000")
        BigDecimal lowStockThreshold,

        LocalDate expiresAt,

        @Size(max = 255)
        String note
) {
}
