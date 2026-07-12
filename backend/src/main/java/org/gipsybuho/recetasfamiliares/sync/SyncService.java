package org.gipsybuho.recetasfamiliares.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeEntity;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeRepository;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeResponse;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.menus.MenuItemEntity;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemResponse;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteEntity;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteResponse;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoEntity;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoRepository;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoResponse;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoService;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepResponse;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListEntity;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemEntity;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemResponse;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListResponse;
import org.gipsybuho.recetasfamiliares.stock.StockItemEntity;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemResponse;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SyncService {

    private static final Instant DEFAULT_SINCE = Instant.EPOCH;
    static final int DEFAULT_PULL_LIMIT = 200;
    static final int MAX_PULL_LIMIT = 500;

    /** Orden total estable para paginacion por cursor de updatedAt. */
    private static final Sort PULL_SORT = Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id"));

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final StockItemRepository stockItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final FamilyNoteRepository familyNoteRepository;
    private final RecipePhotoRepository photoRepository;
    private final UserRepository userRepository;

    public SyncService(
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository,
            RecipeRepository recipeRepository,
            RecipeIngredientRepository ingredientRepository,
            RecipeStepRepository stepRepository,
            StockItemRepository stockItemRepository,
            MenuItemRepository menuItemRepository,
            ShoppingListRepository shoppingListRepository,
            ShoppingListItemRepository shoppingListItemRepository,
            FavoriteRecipeRepository favoriteRecipeRepository,
            FamilyNoteRepository familyNoteRepository,
            RecipePhotoRepository photoRepository,
            UserRepository userRepository
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.stockItemRepository = stockItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingListItemRepository = shoppingListItemRepository;
        this.favoriteRecipeRepository = favoriteRecipeRepository;
        this.familyNoteRepository = familyNoteRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SyncPullResponse pull(String familyId, String userId, Instant since, Integer limit) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
        Instant effectiveSince = since == null ? DEFAULT_SINCE : since;
        Instant serverTime = Instant.now();
        int effectiveLimit = limit == null ? DEFAULT_PULL_LIMIT : normalizeLimit(limit);
        return pagedPull(familyId, effectiveSince, effectiveLimit, serverTime);
    }

    private SyncPullResponse pagedPull(String familyId, Instant since, int limit, Instant serverTime) {
        Slice<RecipeResponse> recipes = fetchSlice(
                p -> recipeRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                RecipeEntity::getUpdatedAt, this::toRecipeResponse, limit);
        Slice<RecipeIngredientResponse> ingredients = fetchSlice(
                p -> ingredientRepository.findByRecipe_Family_IdAndUpdatedAtAfter(familyId, since, p),
                RecipeIngredientEntity::getUpdatedAt, this::toIngredientResponse, limit);
        Slice<RecipeStepResponse> steps = fetchSlice(
                p -> stepRepository.findByRecipe_Family_IdAndUpdatedAtAfter(familyId, since, p),
                RecipeStepEntity::getUpdatedAt, this::toStepResponse, limit);
        Slice<StockItemResponse> stockItems = fetchSlice(
                p -> stockItemRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                StockItemEntity::getUpdatedAt, this::toStockItemResponse, limit);
        Slice<MenuItemResponse> menuItems = fetchSlice(
                p -> menuItemRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                MenuItemEntity::getUpdatedAt, this::toMenuItemResponse, limit);
        Slice<ShoppingListResponse> shoppingLists = fetchSlice(
                p -> shoppingListRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                ShoppingListEntity::getUpdatedAt, this::toShoppingListResponse, limit);
        Slice<ShoppingListItemResponse> shoppingListItems = fetchSlice(
                p -> shoppingListItemRepository.findByShoppingList_Family_IdAndUpdatedAtAfter(familyId, since, p),
                ShoppingListItemEntity::getUpdatedAt, this::toShoppingListItemResponse, limit);
        Slice<FavoriteRecipeResponse> favoriteRecipes = fetchSlice(
                p -> favoriteRecipeRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                FavoriteRecipeEntity::getUpdatedAt, this::toFavoriteRecipeResponse, limit);
        Slice<FamilyNoteResponse> familyNotes = fetchSlice(
                p -> familyNoteRepository.findByFamily_IdAndUpdatedAtAfter(familyId, since, p),
                FamilyNoteEntity::getUpdatedAt, this::toFamilyNoteResponse, limit);
        Slice<RecipePhotoResponse> recipePhotos = fetchSlice(
                p -> photoRepository.findByRecipe_Family_IdAndUpdatedAtAfter(familyId, since, p),
                RecipePhotoEntity::getUpdatedAt, this::toRecipePhotoResponse, limit);

        // Cursor global: el minimo de los cursores de las listas truncadas. Las listas
        // completas pueden reenviar filas en la pagina siguiente; el upsert idempotente
        // de los clientes lo tolera. Nunca se pierde una fila.
        Instant nextSince = Stream.of(
                        recipes.nextSince(), ingredients.nextSince(), steps.nextSince(),
                        stockItems.nextSince(), menuItems.nextSince(), shoppingLists.nextSince(),
                        shoppingListItems.nextSince(), favoriteRecipes.nextSince(),
                        familyNotes.nextSince(), recipePhotos.nextSince())
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        return new SyncPullResponse(
                serverTime,
                recipes.items(),
                ingredients.items(),
                steps.items(),
                stockItems.items(),
                menuItems.items(),
                shoppingLists.items(),
                shoppingListItems.items(),
                favoriteRecipes.items(),
                familyNotes.items(),
                recipePhotos.items(),
                nextSince != null,
                nextSince
        );
    }

    /** Lista parcial de una entidad; nextSince null significa que no quedan filas pendientes. */
    private record Slice<R>(List<R> items, Instant nextSince) {
    }

    /**
     * Recorta la lista a un grupo completo de updatedAt: nunca parte un grupo de filas
     * con el mismo timestamp, de modo que el cursor estricto (updatedAt > nextSince)
     * no pierda filas y siempre avance. Si todo el bloque comparte timestamp, se
     * entrega el grupo entero aunque supere el limite.
     */
    private <E, R> Slice<R> fetchSlice(
            Function<Pageable, List<E>> query,
            Function<E, Instant> updatedAt,
            Function<E, R> mapper,
            int limit
    ) {
        List<E> fetched = query.apply(PageRequest.of(0, limit + 1, PULL_SORT));
        if (fetched.size() <= limit) {
            return new Slice<>(fetched.stream().map(mapper).toList(), null);
        }
        List<E> page = fetched.subList(0, limit);
        Instant boundary = updatedAt.apply(page.get(limit - 1));
        int cut = limit;
        while (cut > 0 && updatedAt.apply(page.get(cut - 1)).equals(boundary)) {
            cut--;
        }
        if (cut > 0) {
            List<R> items = page.subList(0, cut).stream().map(mapper).toList();
            return new Slice<>(items, updatedAt.apply(page.get(cut - 1)));
        }
        // Caso degenerado: las `limit` primeras filas comparten updatedAt. Extender la
        // lectura hasta cerrar el grupo para poder avanzar el cursor sin perder filas.
        List<E> all = new ArrayList<>(fetched);
        int pageNum = 1;
        boolean exhausted = false;
        while (!exhausted && !updatedAt.apply(all.get(all.size() - 1)).isAfter(boundary)) {
            List<E> next = query.apply(PageRequest.of(pageNum++, limit + 1, PULL_SORT));
            all.addAll(next);
            exhausted = next.size() < limit + 1;
        }
        List<E> kept = all.stream().filter(e -> !updatedAt.apply(e).isAfter(boundary)).toList();
        boolean more = all.size() > kept.size();
        return new Slice<>(kept.stream().map(mapper).toList(), more ? boundary : null);
    }

    private static int normalizeLimit(int limit) {
        if (limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be >= 1");
        }
        return Math.min(limit, MAX_PULL_LIMIT);
    }

    @Transactional
    public SyncPullResponse push(String familyId, String userId, SyncPushRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        UserEntity pushUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        List<RecipeResponse> recipes = new ArrayList<>();
        for (SyncRecipePushItem item : request.recipes()) {
            upsertRecipe(family, pushUser, item).ifPresent(recipe -> recipes.add(toRecipeResponse(recipe)));
        }

        List<RecipeIngredientResponse> ingredients = new ArrayList<>();
        for (SyncIngredientPushItem item : request.ingredients()) {
            upsertIngredient(familyId, item).ifPresent(ingredient -> ingredients.add(toIngredientResponse(ingredient)));
        }

        List<RecipeStepResponse> steps = new ArrayList<>();
        for (SyncStepPushItem item : request.steps()) {
            upsertStep(familyId, item).ifPresent(step -> steps.add(toStepResponse(step)));
        }

        List<StockItemResponse> stockItems = new ArrayList<>();
        for (SyncStockItemPushItem item : optionalStockItems(request)) {
            upsertStockItem(family, item).ifPresent(stockItem -> stockItems.add(toStockItemResponse(stockItem)));
        }

        List<MenuItemResponse> menuItems = new ArrayList<>();
        for (SyncMenuItemPushItem item : optionalMenuItems(request)) {
            upsertMenuItem(family, item).ifPresent(menuItem -> menuItems.add(toMenuItemResponse(menuItem)));
        }

        List<ShoppingListResponse> shoppingLists = new ArrayList<>();
        for (SyncShoppingListPushItem item : optionalShoppingLists(request)) {
            upsertShoppingList(family, item).ifPresent(shoppingList -> shoppingLists.add(toShoppingListResponse(shoppingList)));
        }

        List<ShoppingListItemResponse> shoppingListItems = new ArrayList<>();
        for (SyncShoppingListItemPushItem item : optionalShoppingListItems(request)) {
            upsertShoppingListItem(family.getId(), item).ifPresent(listItem -> shoppingListItems.add(toShoppingListItemResponse(listItem)));
        }

        List<FavoriteRecipeResponse> favoriteRecipes = new ArrayList<>();
        for (SyncFavoriteRecipePushItem item : optionalFavoriteRecipes(request)) {
            upsertFavoriteRecipe(family, item).ifPresent(favorite -> favoriteRecipes.add(toFavoriteRecipeResponse(favorite)));
        }

        List<FamilyNoteResponse> familyNotes = new ArrayList<>();
        for (SyncFamilyNotePushItem item : optionalFamilyNotes(request)) {
            upsertFamilyNote(family, item).ifPresent(note -> familyNotes.add(toFamilyNoteResponse(note)));
        }

        List<RecipePhotoResponse> recipePhotos = new ArrayList<>();
        for (SyncRecipePhotoPushItem item : optionalRecipePhotos(request)) {
            upsertRecipePhoto(family.getId(), item).ifPresent(photo -> recipePhotos.add(toRecipePhotoResponse(photo)));
        }

        return new SyncPullResponse(
                Instant.now(),
                recipes,
                ingredients,
                steps,
                stockItems,
                menuItems,
                shoppingLists,
                shoppingListItems,
                favoriteRecipes,
                familyNotes,
                recipePhotos
        );
    }

    private Optional<RecipeEntity> upsertRecipe(FamilyEntity family, UserEntity pushUser, SyncRecipePushItem item) {
        Optional<RecipeEntity> existing = recipeRepository.findByIdAndFamily_Id(item.id(), family.getId());
        existing.ifPresent(recipe -> requireNoConflict(recipe.getSyncVersion(), item.baseSyncVersion(), "Recipe conflict"));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            RecipeEntity recipe = existing.get();
            softDeleteRecipeContents(recipe.getId());
            recipe.softDelete();
            return Optional.of(recipeRepository.save(recipe));
        }
        requireRecipePayload(item);
        RecipeEntity recipe = existing.orElseGet(() -> new RecipeEntity(
                item.id(),
                family,
                pushUser,
                trimRequired(item.title(), "Recipe title is required"),
                trimToNull(item.description()),
                item.servings(),
                item.prepMinutes(),
                item.cookMinutes(),
                item.difficulty()
        ));
        recipe.applySync(
                trimRequired(item.title(), "Recipe title is required"),
                trimToNull(item.description()),
                item.servings(),
                item.prepMinutes(),
                item.cookMinutes(),
                item.difficulty(),
                item.deleted()
        );
        return Optional.of(recipeRepository.save(recipe));
    }

    private Optional<RecipeIngredientEntity> upsertIngredient(String familyId, SyncIngredientPushItem item) {
        Optional<RecipeIngredientEntity> existing = ingredientRepository.findByIdAndRecipe_Family_Id(item.id(), familyId);
        existing.ifPresent(ingredient -> requireNoConflict(
                ingredient.getSyncVersion(),
                item.baseSyncVersion(),
                "Recipe ingredient conflict"
        ));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            RecipeIngredientEntity ingredient = existing.get();
            requireSameRecipe(ingredient.getRecipeId(), item.recipeId());
            ingredient.softDelete();
            touchRecipe(familyId, item.recipeId(), "Recipe not found for ingredient");
            return Optional.of(ingredientRepository.save(ingredient));
        }
        RecipeEntity recipe = recipeRepository.findByIdAndFamily_Id(item.recipeId(), familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for ingredient"));
        if (recipe.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sync active ingredient for deleted recipe");
        }
        requireIngredientPayload(item);
        existing.ifPresent(ingredient -> requireSameRecipe(ingredient.getRecipeId(), item.recipeId()));
        RecipeIngredientEntity ingredient = existing.orElseGet(() -> new RecipeIngredientEntity(
                item.id(),
                recipe,
                item.position(),
                trimRequired(item.name(), "Ingredient name is required"),
                item.quantity(),
                trimToNull(item.unit()),
                trimToNull(item.note())
        ));
        ingredient.applySync(
                item.position(),
                trimRequired(item.name(), "Ingredient name is required"),
                item.quantity(),
                trimToNull(item.unit()),
                trimToNull(item.note()),
                item.deleted()
        );
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return Optional.of(ingredientRepository.save(ingredient));
    }

    private Optional<RecipeStepEntity> upsertStep(String familyId, SyncStepPushItem item) {
        Optional<RecipeStepEntity> existing = stepRepository.findByIdAndRecipe_Family_Id(item.id(), familyId);
        existing.ifPresent(step -> requireNoConflict(step.getSyncVersion(), item.baseSyncVersion(), "Recipe step conflict"));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            RecipeStepEntity step = existing.get();
            requireSameRecipe(step.getRecipeId(), item.recipeId());
            step.softDelete();
            touchRecipe(familyId, item.recipeId(), "Recipe not found for step");
            return Optional.of(stepRepository.save(step));
        }
        RecipeEntity recipe = recipeRepository.findByIdAndFamily_Id(item.recipeId(), familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for step"));
        if (recipe.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sync active step for deleted recipe");
        }
        requireStepPayload(item);
        existing.ifPresent(step -> requireSameRecipe(step.getRecipeId(), item.recipeId()));
        RecipeStepEntity step = existing.orElseGet(() -> new RecipeStepEntity(
                item.id(),
                recipe,
                item.position(),
                trimRequired(item.instruction(), "Step instruction is required"),
                item.timerMinutes()
        ));
        step.applySync(
                item.position(),
                trimRequired(item.instruction(), "Step instruction is required"),
                item.timerMinutes(),
                item.deleted()
        );
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return Optional.of(stepRepository.save(step));
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

    private void touchRecipe(String familyId, String recipeId, String message) {
        RecipeEntity recipe = recipeRepository.findByIdAndFamily_Id(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
        recipe.markContentChanged();
        recipeRepository.save(recipe);
    }

    private void softDeleteRecipeContents(String recipeId) {
        ingredientRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipeIngredientEntity::softDelete);
        stepRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipeStepEntity::softDelete);
        photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipePhotoEntity::softDelete);
    }

    private static void requireSameRecipe(String existingRecipeId, String requestedRecipeId) {
        if (!existingRecipeId.equals(requestedRecipeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Synced content cannot change recipe");
        }
    }

    private RecipeResponse toRecipeResponse(RecipeEntity recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getFamilyId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getServings(),
                recipe.getPrepMinutes(),
                recipe.getCookMinutes(),
                recipe.getDifficulty(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                recipe.getSyncVersion(),
                recipe.isDeleted(),
                recipe.getCreatedByUserId(),
                recipe.getCreatedByDisplayName()
        );
    }

    private RecipeIngredientResponse toIngredientResponse(RecipeIngredientEntity ingredient) {
        return new RecipeIngredientResponse(
                ingredient.getId(),
                ingredient.getRecipeId(),
                ingredient.getPosition(),
                ingredient.getName(),
                ingredient.getQuantity(),
                ingredient.getUnit(),
                ingredient.getNote(),
                ingredient.getCreatedAt(),
                ingredient.getUpdatedAt(),
                ingredient.getSyncVersion(),
                ingredient.isDeleted()
        );
    }

    private RecipeStepResponse toStepResponse(RecipeStepEntity step) {
        return new RecipeStepResponse(
                step.getId(),
                step.getRecipeId(),
                step.getPosition(),
                step.getInstruction(),
                step.getTimerMinutes(),
                step.getCreatedAt(),
                step.getUpdatedAt(),
                step.getSyncVersion(),
                step.isDeleted()
        );
    }

    private Optional<StockItemEntity> upsertStockItem(FamilyEntity family, SyncStockItemPushItem item) {
        Optional<StockItemEntity> existing = stockItemRepository.findByIdAndFamily_Id(item.id(), family.getId());
        existing.ifPresent(stockItem -> requireNoConflict(
                stockItem.getSyncVersion(),
                item.baseSyncVersion(),
                "Stock item conflict"
        ));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            StockItemEntity stockItem = existing.get();
            stockItem.softDelete();
            return Optional.of(stockItemRepository.save(stockItem));
        }
        requireStockItemPayload(item);
        StockItemEntity stockItem = existing.orElseGet(() -> new StockItemEntity(
                item.id(),
                family,
                trimRequired(item.name(), "Stock item name is required"),
                item.quantity(),
                trimToNull(item.unit()),
                item.lowStockThreshold(),
                item.expiresAt(),
                trimToNull(item.note())
        ));
        stockItem.update(
                trimRequired(item.name(), "Stock item name is required"),
                item.quantity(),
                trimToNull(item.unit()),
                item.lowStockThreshold(),
                item.expiresAt(),
                trimToNull(item.note())
        );
        return Optional.of(stockItemRepository.save(stockItem));
    }

    private StockItemResponse toStockItemResponse(StockItemEntity stockItem) {
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

    private Optional<MenuItemEntity> upsertMenuItem(FamilyEntity family, SyncMenuItemPushItem item) {
        Optional<MenuItemEntity> existing = menuItemRepository.findByIdAndFamily_Id(item.id(), family.getId());
        existing.ifPresent(menuItem -> requireNoConflict(menuItem.getSyncVersion(), item.baseSyncVersion(), "Menu item conflict"));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            MenuItemEntity menuItem = existing.get();
            menuItem.softDelete();
            return Optional.of(menuItemRepository.save(menuItem));
        }
        requireMenuItemPayload(item);
        RecipeEntity recipe = resolveActiveRecipe(family.getId(), item.recipeId());
        MenuItemEntity menuItem = existing.orElseGet(() -> new MenuItemEntity(
                item.id(),
                family,
                recipe,
                item.plannedDate(),
                item.mealType(),
                trimToNull(item.note())
        ));
        menuItem.update(recipe, item.plannedDate(), item.mealType(), trimToNull(item.note()));
        return Optional.of(menuItemRepository.save(menuItem));
    }

    private RecipeEntity resolveActiveRecipe(String familyId, String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for menu item"));
    }

    private MenuItemResponse toMenuItemResponse(MenuItemEntity menuItem) {
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

    private Optional<ShoppingListEntity> upsertShoppingList(FamilyEntity family, SyncShoppingListPushItem item) {
        Optional<ShoppingListEntity> existing = shoppingListRepository.findByIdAndFamily_Id(item.id(), family.getId());
        existing.ifPresent(shoppingList -> requireNoConflict(
                shoppingList.getSyncVersion(),
                item.baseSyncVersion(),
                "Shopping list conflict"
        ));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            ShoppingListEntity shoppingList = existing.get();
            softDeleteShoppingListItems(shoppingList.getId());
            shoppingList.softDelete();
            return Optional.of(shoppingListRepository.save(shoppingList));
        }
        requireShoppingListPayload(item);
        ShoppingListEntity shoppingList = existing.orElseGet(() -> new ShoppingListEntity(
                item.id(),
                family,
                trimRequired(item.name(), "Shopping list name is required"),
                item.plannedFrom(),
                item.plannedTo(),
                trimToNull(item.note()),
                item.completed()
        ));
        shoppingList.update(
                trimRequired(item.name(), "Shopping list name is required"),
                item.plannedFrom(),
                item.plannedTo(),
                trimToNull(item.note()),
                item.completed()
        );
        return Optional.of(shoppingListRepository.save(shoppingList));
    }

    private Optional<ShoppingListItemEntity> upsertShoppingListItem(String familyId, SyncShoppingListItemPushItem item) {
        Optional<ShoppingListItemEntity> existing = shoppingListItemRepository.findByIdAndShoppingList_Family_Id(
                item.id(),
                familyId
        );
        existing.ifPresent(listItem -> requireNoConflict(
                listItem.getSyncVersion(),
                item.baseSyncVersion(),
                "Shopping list item conflict"
        ));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            ShoppingListItemEntity listItem = existing.get();
            requireSameShoppingList(listItem.getShoppingListId(), item.shoppingListId());
            listItem.softDelete();
            touchShoppingList(familyId, item.shoppingListId(), "Shopping list not found for item");
            return Optional.of(shoppingListItemRepository.save(listItem));
        }
        ShoppingListEntity shoppingList = shoppingListRepository.findByIdAndFamily_Id(item.shoppingListId(), familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shopping list not found for item"));
        if (shoppingList.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sync active item for deleted shopping list");
        }
        requireShoppingListItemPayload(item);
        existing.ifPresent(listItem -> requireSameShoppingList(listItem.getShoppingListId(), item.shoppingListId()));
        ShoppingListItemEntity listItem = existing.orElseGet(() -> new ShoppingListItemEntity(
                item.id(),
                shoppingList,
                item.position(),
                trimRequired(item.name(), "Shopping list item name is required"),
                item.quantity(),
                trimToNull(item.unit()),
                item.checked(),
                trimToNull(item.note())
        ));
        listItem.update(
                item.position(),
                trimRequired(item.name(), "Shopping list item name is required"),
                item.quantity(),
                trimToNull(item.unit()),
                item.checked(),
                trimToNull(item.note())
        );
        shoppingList.markItemsChanged();
        shoppingListRepository.save(shoppingList);
        return Optional.of(shoppingListItemRepository.save(listItem));
    }

    private void touchShoppingList(String familyId, String shoppingListId, String message) {
        ShoppingListEntity shoppingList = shoppingListRepository.findByIdAndFamily_Id(shoppingListId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
        shoppingList.markItemsChanged();
        shoppingListRepository.save(shoppingList);
    }

    private void softDeleteShoppingListItems(String shoppingListId) {
        shoppingListItemRepository.findByShoppingList_IdAndDeletedFalseOrderByPositionAsc(shoppingListId)
                .forEach(ShoppingListItemEntity::softDelete);
    }

    private static void requireSameShoppingList(String existingShoppingListId, String requestedShoppingListId) {
        if (!existingShoppingListId.equals(requestedShoppingListId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Synced shopping item cannot change list");
        }
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

    private ShoppingListItemResponse toShoppingListItemResponse(ShoppingListItemEntity item) {
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

    private Optional<FavoriteRecipeEntity> upsertFavoriteRecipe(FamilyEntity family, SyncFavoriteRecipePushItem item) {
        Optional<FavoriteRecipeEntity> existing = favoriteRecipeRepository.findByIdAndFamily_Id(item.id(), family.getId());
        existing.ifPresent(favorite -> requireNoConflict(
                favorite.getSyncVersion(),
                item.baseSyncVersion(),
                "Favorite recipe conflict"
        ));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            FavoriteRecipeEntity favorite = existing.get();
            if (item.recipeId() != null && !item.recipeId().isBlank()) {
                requireSameRecipe(favorite.getRecipeId(), item.recipeId());
            }
            favorite.softDelete();
            return Optional.of(favoriteRecipeRepository.save(favorite));
        }
        requireFavoriteRecipePayload(item);
        RecipeEntity recipe = recipeRepository.findByIdAndFamily_IdAndDeletedFalse(item.recipeId(), family.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for favorite"));
        FavoriteRecipeEntity favorite = existing.orElseGet(() -> new FavoriteRecipeEntity(item.id(), family, recipe));
        favorite.restore();
        return Optional.of(favoriteRecipeRepository.save(favorite));
    }

    private FavoriteRecipeResponse toFavoriteRecipeResponse(FavoriteRecipeEntity favorite) {
        return new FavoriteRecipeResponse(
                favorite.getId(),
                favorite.getFamilyId(),
                favorite.getRecipeId(),
                favorite.getRecipeTitle(),
                favorite.getCreatedAt(),
                favorite.getUpdatedAt(),
                favorite.getSyncVersion(),
                favorite.isDeleted()
        );
    }

    private Optional<FamilyNoteEntity> upsertFamilyNote(FamilyEntity family, SyncFamilyNotePushItem item) {
        Optional<FamilyNoteEntity> existing = familyNoteRepository.findByIdAndFamily_Id(item.id(), family.getId());
        existing.ifPresent(note -> requireNoConflict(note.getSyncVersion(), item.baseSyncVersion(), "Family note conflict"));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            FamilyNoteEntity note = existing.get();
            note.softDelete();
            return Optional.of(familyNoteRepository.save(note));
        }
        requireFamilyNotePayload(item);
        RecipeEntity recipe = resolveOptionalRecipe(family.getId(), item.recipeId(), "Recipe not found for note");
        FamilyNoteEntity note = existing.orElseGet(() -> new FamilyNoteEntity(
                item.id(),
                family,
                recipe,
                trimRequired(item.title(), "Family note title is required"),
                trimRequired(item.body(), "Family note body is required"),
                item.pinned()
        ));
        note.update(
                recipe,
                trimRequired(item.title(), "Family note title is required"),
                trimRequired(item.body(), "Family note body is required"),
                item.pinned()
        );
        return Optional.of(familyNoteRepository.save(note));
    }

    private RecipeEntity resolveOptionalRecipe(String familyId, String recipeId, String message) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
    }

    private FamilyNoteResponse toFamilyNoteResponse(FamilyNoteEntity note) {
        return new FamilyNoteResponse(
                note.getId(),
                note.getFamilyId(),
                note.getRecipeId(),
                note.getRecipeTitle(),
                note.getTitle(),
                note.getBody(),
                note.isPinned(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                note.getSyncVersion(),
                note.isDeleted()
        );
    }

    private Optional<RecipePhotoEntity> upsertRecipePhoto(String familyId, SyncRecipePhotoPushItem item) {
        Optional<RecipePhotoEntity> existing = photoRepository.findByIdAndRecipe_Family_Id(item.id(), familyId);
        existing.ifPresent(photo -> requireNoConflict(photo.getSyncVersion(), item.baseSyncVersion(), "Recipe photo conflict"));
        if (existing.isEmpty() && item.deleted()) {
            return Optional.empty();
        }
        if (existing.isPresent() && item.deleted()) {
            RecipePhotoEntity photo = existing.get();
            if (item.recipeId() != null && !item.recipeId().isBlank()) {
                requireSameRecipe(photo.getRecipeId(), item.recipeId());
            }
            photo.softDelete();
            touchRecipe(familyId, photo.getRecipeId(), "Recipe not found for photo");
            return Optional.of(photoRepository.save(photo));
        }
        requireRecipePhotoPayload(item);
        RecipeEntity recipe = recipeRepository.findByIdAndFamily_Id(item.recipeId(), familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for photo"));
        if (recipe.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sync active photo for deleted recipe");
        }
        existing.ifPresent(photo -> requireSameRecipe(photo.getRecipeId(), item.recipeId()));
        RecipePhotoService.validateMetadata(item.url(), item.thumbnailUrl(), item.contentType());
        RecipePhotoEntity photo = existing.orElseGet(() -> new RecipePhotoEntity(
                item.id(),
                recipe,
                item.position(),
                item.url().trim(),
                trimToNull(item.thumbnailUrl()),
                trimToNull(item.caption()),
                trimToNull(item.contentType()),
                item.sizeBytes()
        ));
        photo.update(
                item.position(),
                item.url().trim(),
                trimToNull(item.thumbnailUrl()),
                trimToNull(item.caption()),
                trimToNull(item.contentType()),
                item.sizeBytes()
        );
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return Optional.of(photoRepository.save(photo));
    }

    private RecipePhotoResponse toRecipePhotoResponse(RecipePhotoEntity photo) {
        return new RecipePhotoResponse(
                photo.getId(),
                photo.getRecipeId(),
                photo.getPosition(),
                photo.getUrl(),
                photo.getThumbnailUrl(),
                photo.getCaption(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getCreatedAt(),
                photo.getUpdatedAt(),
                photo.getSyncVersion(),
                photo.isDeleted()
        );
    }

    private static void requireRecipePayload(SyncRecipePushItem item) {
        trimRequired(item.title(), "Recipe title is required");
    }

    private static void requireNoConflict(long serverSyncVersion, Long baseSyncVersion, String message) {
        if (baseSyncVersion != null && baseSyncVersion != serverSyncVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private static void requireIngredientPayload(SyncIngredientPushItem item) {
        trimRequired(item.name(), "Ingredient name is required");
    }

    private static void requireStepPayload(SyncStepPushItem item) {
        trimRequired(item.instruction(), "Step instruction is required");
    }

    private static void requireStockItemPayload(SyncStockItemPushItem item) {
        trimRequired(item.name(), "Stock item name is required");
    }

    private static void requireMenuItemPayload(SyncMenuItemPushItem item) {
        if (item.plannedDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menu item planned date is required");
        }
        if (item.mealType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menu item meal type is required");
        }
    }

    private static void requireShoppingListPayload(SyncShoppingListPushItem item) {
        trimRequired(item.name(), "Shopping list name is required");
    }

    private static void requireShoppingListItemPayload(SyncShoppingListItemPushItem item) {
        if (item.shoppingListId() == null || item.shoppingListId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shopping list id is required");
        }
        trimRequired(item.name(), "Shopping list item name is required");
    }

    private static void requireFavoriteRecipePayload(SyncFavoriteRecipePushItem item) {
        if (item.recipeId() == null || item.recipeId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Favorite recipe id is required");
        }
    }

    private static void requireFamilyNotePayload(SyncFamilyNotePushItem item) {
        trimRequired(item.title(), "Family note title is required");
        trimRequired(item.body(), "Family note body is required");
    }

    private static void requireRecipePhotoPayload(SyncRecipePhotoPushItem item) {
        if (item.recipeId() == null || item.recipeId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe id is required for photo");
        }
        if (item.position() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo position is required");
        }
        trimRequired(item.url(), "Photo URL is required");
    }

    private static List<SyncStockItemPushItem> optionalStockItems(SyncPushRequest request) {
        if (request.stockItems() == null) {
            return Collections.emptyList();
        }
        return request.stockItems();
    }

    private static List<SyncMenuItemPushItem> optionalMenuItems(SyncPushRequest request) {
        if (request.menuItems() == null) {
            return Collections.emptyList();
        }
        return request.menuItems();
    }

    private static List<SyncShoppingListPushItem> optionalShoppingLists(SyncPushRequest request) {
        if (request.shoppingLists() == null) {
            return Collections.emptyList();
        }
        return request.shoppingLists();
    }

    private static List<SyncShoppingListItemPushItem> optionalShoppingListItems(SyncPushRequest request) {
        if (request.shoppingListItems() == null) {
            return Collections.emptyList();
        }
        return request.shoppingListItems();
    }

    private static List<SyncFavoriteRecipePushItem> optionalFavoriteRecipes(SyncPushRequest request) {
        if (request.favoriteRecipes() == null) {
            return Collections.emptyList();
        }
        return request.favoriteRecipes();
    }

    private static List<SyncFamilyNotePushItem> optionalFamilyNotes(SyncPushRequest request) {
        if (request.familyNotes() == null) {
            return Collections.emptyList();
        }
        return request.familyNotes();
    }

    private static List<SyncRecipePhotoPushItem> optionalRecipePhotos(SyncPushRequest request) {
        if (request.recipePhotos() == null) {
            return Collections.emptyList();
        }
        return request.recipePhotos();
    }

    private static String trimRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
