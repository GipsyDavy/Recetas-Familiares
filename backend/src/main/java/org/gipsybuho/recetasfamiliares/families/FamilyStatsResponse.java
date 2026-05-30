package org.gipsybuho.recetasfamiliares.families;

import java.time.Instant;

public record FamilyStatsResponse(
        long totalRecipes,
        Instant lastActivityAt
) {}
