package org.gipsybuho.recetasfamiliares.menus;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/families/{familyId}/menu-items")
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @GetMapping
    public PageResponse<MenuItemResponse> listMenuItems(
            @PathVariable String familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return menuItemService.listMenuItems(familyId, authentication.getName(), weekStart, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createMenuItem(
            @PathVariable String familyId,
            @Valid @RequestBody CreateMenuItemRequest request,
            Authentication authentication
    ) {
        return menuItemService.createMenuItem(familyId, authentication.getName(), request);
    }

    @GetMapping("/{menuItemId}")
    public MenuItemResponse getMenuItem(
            @PathVariable String familyId,
            @PathVariable String menuItemId,
            Authentication authentication
    ) {
        return menuItemService.getMenuItem(familyId, menuItemId, authentication.getName());
    }

    @PutMapping("/{menuItemId}")
    public MenuItemResponse updateMenuItem(
            @PathVariable String familyId,
            @PathVariable String menuItemId,
            @Valid @RequestBody UpdateMenuItemRequest request,
            Authentication authentication
    ) {
        return menuItemService.updateMenuItem(familyId, menuItemId, authentication.getName(), request);
    }

    @DeleteMapping("/{menuItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenuItem(
            @PathVariable String familyId,
            @PathVariable String menuItemId,
            Authentication authentication
    ) {
        menuItemService.deleteMenuItem(familyId, menuItemId, authentication.getName());
    }
}
