package org.gipsybuho.recetasfamiliares.shopping;

import java.time.Instant;
import java.time.LocalDate;

public record ShoppingListResponse(
        String id,
        String familyId,
        String name,
        LocalDate plannedFrom,
        LocalDate plannedTo,
        String note,
        boolean completed,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
