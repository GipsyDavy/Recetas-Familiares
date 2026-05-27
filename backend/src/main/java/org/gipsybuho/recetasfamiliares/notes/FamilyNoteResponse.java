package org.gipsybuho.recetasfamiliares.notes;

import java.time.Instant;

public record FamilyNoteResponse(
        String id,
        String familyId,
        String recipeId,
        String recipeTitle,
        String title,
        String body,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
