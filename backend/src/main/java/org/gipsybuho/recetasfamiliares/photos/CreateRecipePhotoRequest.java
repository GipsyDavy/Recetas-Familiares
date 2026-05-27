package org.gipsybuho.recetasfamiliares.photos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateRecipePhotoRequest(
        @Min(1)
        int position,

        @NotBlank
        @Size(max = 1000)
        String url,

        @Size(max = 1000)
        String thumbnailUrl,

        @Size(max = 255)
        String caption,

        @Size(max = 80)
        String contentType,

        @PositiveOrZero
        Long sizeBytes
) {
}
