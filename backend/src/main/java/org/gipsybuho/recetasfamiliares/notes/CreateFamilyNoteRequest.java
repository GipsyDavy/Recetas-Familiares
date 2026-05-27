package org.gipsybuho.recetasfamiliares.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFamilyNoteRequest(
        @Size(max = 36)
        String recipeId,

        @NotBlank
        @Size(max = 180)
        String title,

        @NotBlank
        @Size(max = 4000)
        String body,

        boolean pinned
) {
}
