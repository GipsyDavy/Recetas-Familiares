package org.gipsybuho.recetasfamiliares.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SyncFamilyNotePushItem(
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = SyncIds.UUID_V4_PATTERN, message = "must be a UUID v4")
        String id,

        Long baseSyncVersion,

        @Size(max = 36)
        String recipeId,

        @Size(max = 180)
        String title,

        @Size(max = 4000)
        String body,

        boolean pinned,

        boolean deleted
) {
}
