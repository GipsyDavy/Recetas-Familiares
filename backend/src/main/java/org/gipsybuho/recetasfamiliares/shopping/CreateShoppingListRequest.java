package org.gipsybuho.recetasfamiliares.shopping;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShoppingListRequest(
        @NotBlank
        @Size(max = 180)
        String name,

        LocalDate plannedFrom,

        LocalDate plannedTo,

        @Size(max = 255)
        String note,

        boolean completed
) {
}
