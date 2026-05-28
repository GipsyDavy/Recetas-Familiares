package org.gipsybuho.recetasfamiliares.api.dto;

public final class StockDtos {

    private StockDtos() {}

    public record StockItemDto(
            String id,
            String familyId,
            String name,
            Double quantity,
            String unit,
            Double lowStockThreshold,
            String expiresAt,
            String note,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}

    public record CreateStockItemRequest(
            String name,
            Double quantity,
            String unit,
            Double lowStockThreshold,
            String expiresAt,
            String note
    ) {}

    public record UpdateStockItemRequest(
            String name,
            Double quantity,
            String unit,
            Double lowStockThreshold,
            String expiresAt,
            String note
    ) {}
}
