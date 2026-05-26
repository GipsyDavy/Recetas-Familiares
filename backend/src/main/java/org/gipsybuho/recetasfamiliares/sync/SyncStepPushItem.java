package org.gipsybuho.recetasfamiliares.sync;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SyncStepPushItem(
        @NotBlank
        @Size(max = 36)
        String id,

        @NotBlank
        @Size(max = 36)
        String recipeId,

        @Min(1)
        int position,

        @Size(max = 2000)
        String instruction,

        @Min(0)
        Integer timerMinutes,

        boolean deleted
) {
}
