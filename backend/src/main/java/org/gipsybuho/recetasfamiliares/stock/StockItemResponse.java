package org.gipsybuho.recetasfamiliares.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StockItemResponse(
        String id,
        String familyId,
        String name,
        BigDecimal quantity,
        String unit,
        BigDecimal lowStockThreshold,
        LocalDate expiresAt,
        String note,
        Instant createdAt,
        Instant updatedAt,
        long syncVersion,
        boolean deleted
) {
}
