package org.gipsybuho.recetasfamiliares.api.dto;

public final class StockDtos {

    private StockDtos() {}

    public record StockItemDto(
            String id,
            String familyId,
            String name,
            Double quantity,
            String unit,
            String expiresAt,
            String updatedAt,
            long syncVersion,
            boolean deleted
    ) {}
}
