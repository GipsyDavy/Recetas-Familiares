package org.gipsybuho.recetasfamiliares.shopping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.menus.MenuItemEntity;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingListService {

    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository itemRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeIngredientRepository ingredientRepository;

    public ShoppingListService(
            ShoppingListRepository shoppingListRepository,
            ShoppingListItemRepository itemRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            MenuItemRepository menuItemRepository,
            RecipeIngredientRepository ingredientRepository
    ) {
        this.shoppingListRepository = shoppingListRepository;
        this.itemRepository = itemRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.menuItemRepository = menuItemRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ShoppingListResponse> listShoppingLists(String familyId, String userId, int page, int size) {
        requireMembership(familyId, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ShoppingListEntity> shoppingLists = shoppingListRepository.findByFamily_IdAndDeletedFalse(familyId, pageable);
        return new PageResponse<>(
                shoppingLists.getContent().stream().map(this::toShoppingListResponse).toList(),
                shoppingLists.getNumber(),
                shoppingLists.getSize(),
                shoppingLists.getTotalElements(),
                shoppingLists.getTotalPages()
        );
    }

    @Transactional
    public ShoppingListResponse createShoppingList(String familyId, String userId, CreateShoppingListRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        ShoppingListEntity shoppingList = new ShoppingListEntity(
                family,
                request.name().trim(),
                request.plannedFrom(),
                request.plannedTo(),
                trimToNull(request.note()),
                request.completed()
        );
        return toShoppingListResponse(shoppingListRepository.save(shoppingList));
    }

    @Transactional
    public ShoppingListResponse generateFromMenu(String familyId, String userId, GenerateShoppingListRequest request) {
        requireEditor(familyId, userId);
        validateDateRange(request.startDate(), request.endDate());
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        ShoppingListEntity shoppingList = shoppingListRepository.save(new ShoppingListEntity(
                family,
                generatedName(request),
                request.startDate(),
                request.endDate(),
                trimToNull(request.note()),
                false
        ));

        Map<IngredientKey, AggregatedIngredient> ingredients = new LinkedHashMap<>();
        for (MenuItemEntity menuItem : menuItemRepository
                .findByFamily_IdAndPlannedDateBetweenAndDeletedFalseOrderByPlannedDateAscMealTypeAsc(
                        familyId,
                        request.startDate(),
                        request.endDate()
                )) {
            if (menuItem.getRecipeId() == null) {
                continue;
            }
            for (RecipeIngredientEntity ingredient : ingredientRepository
                    .findByRecipe_IdAndDeletedFalseOrderByPositionAsc(menuItem.getRecipeId())) {
                IngredientKey key = new IngredientKey(normalize(ingredient.getName()), normalize(ingredient.getUnit()));
                ingredients.compute(key, (ignored, existing) -> aggregate(existing, ingredient));
            }
        }

        int position = 1;
        for (AggregatedIngredient ingredient : ingredients.values()) {
            itemRepository.save(new ShoppingListItemEntity(
                    shoppingList,
                    position++,
                    ingredient.name(),
                    ingredient.quantity(),
                    ingredient.unit(),
                    false,
                    null
            ));
        }
        if (!ingredients.isEmpty()) {
            shoppingList.markItemsChanged();
            shoppingListRepository.save(shoppingList);
        }
        return toShoppingListResponse(shoppingList);
    }

    @Transactional(readOnly = true)
    public ShoppingListResponse getShoppingList(String familyId, String shoppingListId, String userId) {
        requireMembership(familyId, userId);
        return toShoppingListResponse(requireActiveShoppingList(familyId, shoppingListId));
    }

    @Transactional
    public ShoppingListResponse updateShoppingList(
            String familyId,
            String shoppingListId,
            String userId,
            UpdateShoppingListRequest request
    ) {
        requireEditor(familyId, userId);
        ShoppingListEntity shoppingList = requireActiveShoppingList(familyId, shoppingListId);
        shoppingList.update(
                request.name().trim(),
                request.plannedFrom(),
                request.plannedTo(),
                trimToNull(request.note()),
                request.completed()
        );
        return toShoppingListResponse(shoppingListRepository.save(shoppingList));
    }

    @Transactional
    public void deleteShoppingList(String familyId, String shoppingListId, String userId) {
        requireEditor(familyId, userId);
        ShoppingListEntity shoppingList = requireActiveShoppingList(familyId, shoppingListId);
        itemRepository.findByShoppingList_IdAndDeletedFalseOrderByPositionAsc(shoppingListId)
                .forEach(ShoppingListItemEntity::softDelete);
        shoppingList.softDelete();
        shoppingListRepository.save(shoppingList);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShoppingListItemResponse> listItems(
            String familyId,
            String shoppingListId,
            String userId,
            int page,
            int size
    ) {
        requireMembership(familyId, userId);
        requireActiveShoppingList(familyId, shoppingListId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "position"));
        Page<ShoppingListItemEntity> items = itemRepository.findByShoppingList_IdAndDeletedFalse(shoppingListId, pageable);
        return new PageResponse<>(
                items.getContent().stream().map(this::toItemResponse).toList(),
                items.getNumber(),
                items.getSize(),
                items.getTotalElements(),
                items.getTotalPages()
        );
    }

    @Transactional
    public ShoppingListItemResponse createItem(
            String familyId,
            String shoppingListId,
            String userId,
            CreateShoppingListItemRequest request
    ) {
        requireEditor(familyId, userId);
        ShoppingListEntity shoppingList = requireActiveShoppingList(familyId, shoppingListId);
        ShoppingListItemEntity item = new ShoppingListItemEntity(
                shoppingList,
                request.position(),
                request.name().trim(),
                request.quantity(),
                trimToNull(request.unit()),
                request.checked(),
                trimToNull(request.note())
        );
        shoppingList.markItemsChanged();
        shoppingListRepository.save(shoppingList);
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public ShoppingListItemResponse getItem(String familyId, String shoppingListId, String itemId, String userId) {
        requireMembership(familyId, userId);
        requireActiveShoppingList(familyId, shoppingListId);
        return toItemResponse(requireActiveItem(familyId, shoppingListId, itemId));
    }

    @Transactional
    public ShoppingListItemResponse updateItem(
            String familyId,
            String shoppingListId,
            String itemId,
            String userId,
            UpdateShoppingListItemRequest request
    ) {
        requireEditor(familyId, userId);
        ShoppingListEntity shoppingList = requireActiveShoppingList(familyId, shoppingListId);
        ShoppingListItemEntity item = requireActiveItem(familyId, shoppingListId, itemId);
        item.update(
                request.position(),
                request.name().trim(),
                request.quantity(),
                trimToNull(request.unit()),
                request.checked(),
                trimToNull(request.note())
        );
        shoppingList.markItemsChanged();
        shoppingListRepository.save(shoppingList);
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional
    public void deleteItem(String familyId, String shoppingListId, String itemId, String userId) {
        requireEditor(familyId, userId);
        ShoppingListEntity shoppingList = requireActiveShoppingList(familyId, shoppingListId);
        ShoppingListItemEntity item = requireActiveItem(familyId, shoppingListId, itemId);
        item.softDelete();
        shoppingList.markItemsChanged();
        shoppingListRepository.save(shoppingList);
        itemRepository.save(item);
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

    private ShoppingListEntity requireActiveShoppingList(String familyId, String shoppingListId) {
        return shoppingListRepository.findByIdAndFamily_IdAndDeletedFalse(shoppingListId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list not found"));
    }

    private ShoppingListItemEntity requireActiveItem(String familyId, String shoppingListId, String itemId) {
        return itemRepository.findByIdAndShoppingList_IdAndShoppingList_Family_IdAndDeletedFalse(
                        itemId,
                        shoppingListId,
                        familyId
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list item not found"));
    }

    private ShoppingListResponse toShoppingListResponse(ShoppingListEntity shoppingList) {
        return new ShoppingListResponse(
                shoppingList.getId(),
                shoppingList.getFamilyId(),
                shoppingList.getName(),
                shoppingList.getPlannedFrom(),
                shoppingList.getPlannedTo(),
                shoppingList.getNote(),
                shoppingList.isCompleted(),
                shoppingList.getCreatedAt(),
                shoppingList.getUpdatedAt(),
                shoppingList.getSyncVersion(),
                shoppingList.isDeleted()
        );
    }

    private ShoppingListItemResponse toItemResponse(ShoppingListItemEntity item) {
        return new ShoppingListItemResponse(
                item.getId(),
                item.getShoppingListId(),
                item.getPosition(),
                item.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.isChecked(),
                item.getNote(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getSyncVersion(),
                item.isDeleted()
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shopping list end date cannot be before start date");
        }
    }

    private static String generatedName(GenerateShoppingListRequest request) {
        String name = trimToNull(request.name());
        if (name != null) {
            return name;
        }
        return "Compra " + request.startDate() + " - " + request.endDate();
    }

    private static AggregatedIngredient aggregate(AggregatedIngredient existing, RecipeIngredientEntity ingredient) {
        if (existing == null) {
            return new AggregatedIngredient(
                    ingredient.getName(),
                    ingredient.getQuantity(),
                    trimToNull(ingredient.getUnit())
            );
        }
        BigDecimal quantity = existing.quantity();
        if (quantity != null && ingredient.getQuantity() != null) {
            quantity = quantity.add(ingredient.getQuantity());
        } else if (ingredient.getQuantity() != null) {
            quantity = ingredient.getQuantity();
        }
        return new AggregatedIngredient(existing.name(), quantity, existing.unit());
    }

    private static String normalize(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private record IngredientKey(String name, String unit) {
    }

    private record AggregatedIngredient(String name, BigDecimal quantity, String unit) {
    }
}
