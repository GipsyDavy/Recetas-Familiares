package org.gipsybuho.recetasfamiliares.sync;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SyncIngredientPushItem(
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = SyncIds.UUID_V4_PATTERN, message = "must be a UUID v4")
        String id,

        Long baseSyncVersion,

        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = SyncIds.UUID_V4_PATTERN, message = "must be a UUID v4")
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
