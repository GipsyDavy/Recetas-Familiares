package org.gipsybuho.recetasfamiliares.shopping;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateShoppingListRequest(
        @Size(max = 180)
        String name,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @Size(max = 255)
        String note
) {
}
