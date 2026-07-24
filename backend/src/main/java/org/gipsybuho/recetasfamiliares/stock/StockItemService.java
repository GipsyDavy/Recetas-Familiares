package org.gipsybuho.recetasfamiliares.stock;

import java.util.List;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockItemService {

    private final StockItemRepository stockItemRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final org.gipsybuho.recetasfamiliares.activity.FamilyActivityService familyActivityService;

    public StockItemService(
            StockItemRepository stockItemRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            org.gipsybuho.recetasfamiliares.activity.FamilyActivityService familyActivityService
    ) {
        this.stockItemRepository = stockItemRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyActivityService = familyActivityService;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockItemResponse> listStockItems(String familyId, String userId, int page, int size) {
        requireMembership(familyId, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<StockItemEntity> stockItems = stockItemRepository.findByFamily_IdAndDeletedFalse(familyId, pageable);
        return new PageResponse<>(
                stockItems.getContent().stream().map(this::toResponse).toList(),
                stockItems.getNumber(),
                stockItems.getSize(),
                stockItems.getTotalElements(),
                stockItems.getTotalPages()
        );
    }

    @Transactional
    public StockItemResponse createStockItem(String familyId, String userId, CreateStockItemRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        StockItemEntity stockItem = new StockItemEntity(
                family,
                request.name().trim(),
                request.quantity(),
                trimToNull(request.unit()),
                request.lowStockThreshold(),
                request.expiresAt(),
                trimToNull(request.note())
        );
        StockItemResponse response = toResponse(stockItemRepository.save(stockItem));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.STOCK, userId);
        return response;
    }

    @Transactional(readOnly = true)
    public StockItemResponse getStockItem(String familyId, String stockItemId, String userId) {
        requireMembership(familyId, userId);
        return toResponse(requireActiveStockItem(familyId, stockItemId));
    }

    @Transactional
    public StockItemResponse updateStockItem(
            String familyId,
            String stockItemId,
            String userId,
            UpdateStockItemRequest request
    ) {
        requireEditor(familyId, userId);
        StockItemEntity stockItem = requireActiveStockItem(familyId, stockItemId);
        stockItem.update(
                request.name().trim(),
                request.quantity(),
                trimToNull(request.unit()),
                request.lowStockThreshold(),
                request.expiresAt(),
                trimToNull(request.note())
        );
        StockItemResponse response = toResponse(stockItemRepository.save(stockItem));
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.STOCK, userId);
        return response;
    }

    @Transactional
    public void deleteStockItem(String familyId, String stockItemId, String userId) {
        requireEditor(familyId, userId);
        StockItemEntity stockItem = requireActiveStockItem(familyId, stockItemId);
        stockItem.softDelete();
        stockItemRepository.save(stockItem);
        familyActivityService.recordActivity(familyId, org.gipsybuho.recetasfamiliares.activity.FamilySection.STOCK, userId);
    }

    private void requireMembership(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
    }

    private void requireEditor(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
                familyId,
                userId,
                List.of(FamilyRole.OWNER, FamilyRole.ADMIN)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family write access denied");
        }
    }

    private StockItemEntity requireActiveStockItem(String familyId, String stockItemId) {
        return stockItemRepository.findByIdAndFamily_IdAndDeletedFalse(stockItemId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock item not found"));
    }

    private StockItemResponse toResponse(StockItemEntity stockItem) {
        return new StockItemResponse(
                stockItem.getId(),
                stockItem.getFamilyId(),
                stockItem.getName(),
                stockItem.getQuantity(),
                stockItem.getUnit(),
                stockItem.getLowStockThreshold(),
                stockItem.getExpiresAt(),
                stockItem.getNote(),
                stockItem.getCreatedAt(),
                stockItem.getUpdatedAt(),
                stockItem.getSyncVersion(),
                stockItem.isDeleted()
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
