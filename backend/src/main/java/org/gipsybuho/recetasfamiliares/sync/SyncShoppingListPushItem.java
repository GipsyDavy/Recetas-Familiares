package org.gipsybuho.recetasfamiliares.sync;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SyncShoppingListPushItem(
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = SyncIds.UUID_V4_PATTERN, message = "must be a UUID v4")
        String id,

        Long baseSyncVersion,

        @Size(max = 180)
        String name,

        LocalDate plannedFrom,

        LocalDate plannedTo,

        @Size(max = 255)
        String note,

        boolean completed,

        boolean deleted
) {
}
