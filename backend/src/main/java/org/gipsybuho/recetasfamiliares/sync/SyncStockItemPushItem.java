package org.gipsybuho.recetasfamiliares.sync;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SyncStockItemPushItem(
        @NotBlank
        @Size(max = 36)
        String id,

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
        String note,

        boolean deleted
) {
}
