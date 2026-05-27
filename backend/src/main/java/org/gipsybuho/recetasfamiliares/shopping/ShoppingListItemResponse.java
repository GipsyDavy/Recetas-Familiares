package org.gipsybuho.recetasfamiliares.shopping;

import java.math.BigDecimal;
import java.time.Instant;

public record ShoppingListItemResponse(
        String id,
        String shoppingListId,
        int position,
        String name,
        BigDecimal quantity,
        String unit,
        boolean checked,
        String note,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
