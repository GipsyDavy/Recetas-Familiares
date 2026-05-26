package org.gipsybuho.recetasfamiliares.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.gipsybuho.recetasfamiliares.recipes.RecipeDifficulty;

public record SyncRecipePushItem(
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = SyncIds.UUID_V4_PATTERN, message = "must be a UUID v4")
        String id,

        @Size(max = 180)
        String title,

        @Size(max = 1000)
        String description,

        Integer servings,
        Integer prepMinutes,
        Integer cookMinutes,
        RecipeDifficulty difficulty,
        boolean deleted
) {
}
