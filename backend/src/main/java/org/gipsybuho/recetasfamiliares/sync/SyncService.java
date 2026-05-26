package org.gipsybuho.recetasfamiliares.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeResponse;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepResponse;
import org.gipsybuho.recetasfamiliares.stock.StockItemEntity;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SyncService {

    private static final Instant DEFAULT_SINCE = Instant.EPOCH;

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final StockItemRepository stockItemRepository;

    public SyncService(
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository,
            RecipeRepository recipeRepository,
            RecipeIngredientRepository ingredientRepository,
            RecipeStepRepository stepRepository,
            StockItemRepository stockItemRepository
    ) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.stockItemRepository = stockItemRepository;
    }

    @Transactional(readOnly = true)
    public SyncPullResponse pull(String familyId, String userId, Instant since) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
        Instant effectiveSince = since == null ? DEFAULT_SINCE : since;
        return new SyncPullResponse(
                Instant.now(),
                recipeRepository.findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(familyId, effectiveSince)
                        .stream()
                        .map(this::toRecipeResponse)
                        .toList(),
                ingredientRepository.findByRecipe_Family_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(
                                familyId,
                                effectiveSince
                        )
                        .stream()
                        .map(this::toIngredientResponse)
                        .toList(),
                stepRepository.findByRecipe_Family_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(familyId, effectiveSince)
                        .stream()
                        .map(this::toStepResponse)
                        .toList(),
                stockItemRepository.findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(familyId, effectiveSince)
                        .stream()
                        .map(this::toStockItemResponse)
                        .toList()
        );
    }

    @Transactional
    public SyncPullResponse push(String familyId, String userId, SyncPushRequest request) {
        requireMembership(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));

        List<RecipeResponse> recipes = new ArrayList<>();
        for (SyncRecipePushItem item : request.recipes()) {
            upsertRecipe(family, item).ifPresent(recipe -> recipes.add(toRecipeResponse(recipe)));
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

        return new SyncPullResponse(Instant.now(), recipes, ingredients, steps, stockItems);
    }

    private Optional<RecipeEntity> upsertRecipe(FamilyEntity family, SyncRecipePushItem item) {
        Optional<RecipeEntity> existing = recipeRepository.findByIdAndFamily_Id(item.id(), family.getId());
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
                recipe.isDeleted()
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

    private static void requireRecipePayload(SyncRecipePushItem item) {
        trimRequired(item.title(), "Recipe title is required");
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

    private static List<SyncStockItemPushItem> optionalStockItems(SyncPushRequest request) {
        if (request.stockItems() == null) {
            return Collections.emptyList();
        }
        return request.stockItems();
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
