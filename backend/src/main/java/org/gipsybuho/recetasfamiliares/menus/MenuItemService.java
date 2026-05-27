package org.gipsybuho.recetasfamiliares.menus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final RecipeRepository recipeRepository;

    public MenuItemService(
            MenuItemRepository menuItemRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            RecipeRepository recipeRepository
    ) {
        this.menuItemRepository = menuItemRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponse> listMenuItems(String familyId, String userId, LocalDate weekStart, int page, int size) {
        requireMembership(familyId, userId);
        LocalDate start = normalizeWeekStart(weekStart);
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "plannedDate").and(Sort.by(Sort.Direction.ASC, "mealType"))
        );
        Page<MenuItemEntity> menuItems = menuItemRepository.findByFamily_IdAndPlannedDateBetweenAndDeletedFalse(
                familyId,
                start,
                start.plusDays(6),
                pageable
        );
        return new PageResponse<>(
                menuItems.getContent().stream().map(this::toResponse).toList(),
                menuItems.getNumber(),
                menuItems.getSize(),
                menuItems.getTotalElements(),
                menuItems.getTotalPages()
        );
    }

    @Transactional
    public MenuItemResponse createMenuItem(String familyId, String userId, CreateMenuItemRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        RecipeEntity recipe = resolveActiveRecipe(familyId, request.recipeId());
        MenuItemEntity menuItem = new MenuItemEntity(
                family,
                recipe,
                request.plannedDate(),
                request.mealType(),
                trimToNull(request.note())
        );
        return toResponse(menuItemRepository.save(menuItem));
    }

    @Transactional(readOnly = true)
    public MenuItemResponse getMenuItem(String familyId, String menuItemId, String userId) {
        requireMembership(familyId, userId);
        return toResponse(requireActiveMenuItem(familyId, menuItemId));
    }

    @Transactional
    public MenuItemResponse updateMenuItem(String familyId, String menuItemId, String userId, UpdateMenuItemRequest request) {
        requireEditor(familyId, userId);
        MenuItemEntity menuItem = requireActiveMenuItem(familyId, menuItemId);
        RecipeEntity recipe = resolveActiveRecipe(familyId, request.recipeId());
        menuItem.update(recipe, request.plannedDate(), request.mealType(), trimToNull(request.note()));
        return toResponse(menuItemRepository.save(menuItem));
    }

    @Transactional
    public void deleteMenuItem(String familyId, String menuItemId, String userId) {
        requireEditor(familyId, userId);
        MenuItemEntity menuItem = requireActiveMenuItem(familyId, menuItemId);
        menuItem.softDelete();
        menuItemRepository.save(menuItem);
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

    private MenuItemEntity requireActiveMenuItem(String familyId, String menuItemId) {
        return menuItemRepository.findByIdAndFamily_IdAndDeletedFalse(menuItemId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    }

    private RecipeEntity resolveActiveRecipe(String familyId, String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for menu item"));
    }

    private MenuItemResponse toResponse(MenuItemEntity menuItem) {
        return new MenuItemResponse(
                menuItem.getId(),
                menuItem.getFamilyId(),
                menuItem.getRecipeId(),
                menuItem.getRecipeTitle(),
                menuItem.getPlannedDate(),
                menuItem.getMealType(),
                menuItem.getNote(),
                menuItem.getCreatedAt(),
                menuItem.getUpdatedAt(),
                menuItem.getSyncVersion(),
                menuItem.isDeleted()
        );
    }

    private static LocalDate normalizeWeekStart(LocalDate weekStart) {
        LocalDate date = weekStart == null ? LocalDate.now() : weekStart;
        return date.with(DayOfWeek.MONDAY);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
