package org.gipsybuho.recetasfamiliares.photos;

import java.time.Instant;

public record RecipePhotoResponse(
        String id,
        String recipeId,
        int position,
        String url,
        String thumbnailUrl,
        String caption,
        String contentType,
        Long sizeBytes,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
