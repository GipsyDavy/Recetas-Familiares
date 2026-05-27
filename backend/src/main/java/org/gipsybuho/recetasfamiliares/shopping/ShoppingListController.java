package org.gipsybuho.recetasfamiliares.shopping;

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
@RequestMapping("/api/v1/families/{familyId}/shopping-lists")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @GetMapping
    public PageResponse<ShoppingListResponse> listShoppingLists(
            @PathVariable String familyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return shoppingListService.listShoppingLists(familyId, authentication.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListResponse createShoppingList(
            @PathVariable String familyId,
            @Valid @RequestBody CreateShoppingListRequest request,
            Authentication authentication
    ) {
        return shoppingListService.createShoppingList(familyId, authentication.getName(), request);
    }

    @PostMapping("/generate-from-menu")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListResponse generateFromMenu(
            @PathVariable String familyId,
            @Valid @RequestBody GenerateShoppingListRequest request,
            Authentication authentication
    ) {
        return shoppingListService.generateFromMenu(familyId, authentication.getName(), request);
    }

    @GetMapping("/{shoppingListId}")
    public ShoppingListResponse getShoppingList(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            Authentication authentication
    ) {
        return shoppingListService.getShoppingList(familyId, shoppingListId, authentication.getName());
    }

    @PutMapping("/{shoppingListId}")
    public ShoppingListResponse updateShoppingList(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            @Valid @RequestBody UpdateShoppingListRequest request,
            Authentication authentication
    ) {
        return shoppingListService.updateShoppingList(familyId, shoppingListId, authentication.getName(), request);
    }

    @DeleteMapping("/{shoppingListId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShoppingList(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            Authentication authentication
    ) {
        shoppingListService.deleteShoppingList(familyId, shoppingListId, authentication.getName());
    }

    @GetMapping("/{shoppingListId}/items")
    public PageResponse<ShoppingListItemResponse> listItems(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size,
            Authentication authentication
    ) {
        return shoppingListService.listItems(familyId, shoppingListId, authentication.getName(), page, size);
    }

    @PostMapping("/{shoppingListId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListItemResponse createItem(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            @Valid @RequestBody CreateShoppingListItemRequest request,
            Authentication authentication
    ) {
        return shoppingListService.createItem(familyId, shoppingListId, authentication.getName(), request);
    }

    @GetMapping("/{shoppingListId}/items/{itemId}")
    public ShoppingListItemResponse getItem(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            @PathVariable String itemId,
            Authentication authentication
    ) {
        return shoppingListService.getItem(familyId, shoppingListId, itemId, authentication.getName());
    }

    @PutMapping("/{shoppingListId}/items/{itemId}")
    public ShoppingListItemResponse updateItem(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            @PathVariable String itemId,
            @Valid @RequestBody UpdateShoppingListItemRequest request,
            Authentication authentication
    ) {
        return shoppingListService.updateItem(familyId, shoppingListId, itemId, authentication.getName(), request);
    }

    @DeleteMapping("/{shoppingListId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable String familyId,
            @PathVariable String shoppingListId,
            @PathVariable String itemId,
            Authentication authentication
    ) {
        shoppingListService.deleteItem(familyId, shoppingListId, itemId, authentication.getName());
    }
}
