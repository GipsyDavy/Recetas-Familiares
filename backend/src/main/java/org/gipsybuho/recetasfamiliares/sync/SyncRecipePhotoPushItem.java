package org.gipsybuho.recetasfamiliares.sync;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SyncRecipePhotoPushItem(
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = SyncIds.UUID_V4_PATTERN, message = "must be a UUID v4")
        String id,

        Long baseSyncVersion,

        @Size(max = 36)
        String recipeId,

        @Min(1)
        Integer position,

        @Size(max = 1000)
        String url,

        @Size(max = 1000)
        String thumbnailUrl,

        @Size(max = 255)
        String caption,

        @Size(max = 80)
        String contentType,

        @PositiveOrZero
        Long sizeBytes,

        boolean deleted
) {
}
