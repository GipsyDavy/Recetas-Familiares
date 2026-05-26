package org.gipsybuho.recetasfamiliares.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/families/{familyId}/stock-items")
public class StockItemController {

    private final StockItemService stockItemService;

    public StockItemController(StockItemService stockItemService) {
        this.stockItemService = stockItemService;
    }

    @GetMapping
    public PageResponse<StockItemResponse> listStockItems(
            @PathVariable String familyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return stockItemService.listStockItems(familyId, authentication.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse createStockItem(
            @PathVariable String familyId,
            @Valid @RequestBody CreateStockItemRequest request,
            Authentication authentication
    ) {
        return stockItemService.createStockItem(familyId, authentication.getName(), request);
    }

    @GetMapping("/{stockItemId}")
    public StockItemResponse getStockItem(
            @PathVariable String familyId,
            @PathVariable String stockItemId,
            Authentication authentication
    ) {
        return stockItemService.getStockItem(familyId, stockItemId, authentication.getName());
    }

    @PutMapping("/{stockItemId}")
    public StockItemResponse updateStockItem(
            @PathVariable String familyId,
            @PathVariable String stockItemId,
            @Valid @RequestBody UpdateStockItemRequest request,
            Authentication authentication
    ) {
        return stockItemService.updateStockItem(familyId, stockItemId, authentication.getName(), request);
    }

    @DeleteMapping("/{stockItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStockItem(
            @PathVariable String familyId,
            @PathVariable String stockItemId,
            Authentication authentication
    ) {
        stockItemService.deleteStockItem(familyId, stockItemId, authentication.getName());
    }
}
