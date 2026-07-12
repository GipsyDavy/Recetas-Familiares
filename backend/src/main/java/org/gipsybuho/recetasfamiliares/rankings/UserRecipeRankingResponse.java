package org.gipsybuho.recetasfamiliares.rankings;

public record UserRecipeRankingResponse(
        int rank,
        String userId,
        String displayName,
        String role,
        long recipesCreated,
        long ratingsReceived,
        Double averageStars,
        long score
) {
}
