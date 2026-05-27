package org.gipsybuho.recetasfamiliares.recipes;

import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecipeContentService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public RecipeContentService(
            RecipeRepository recipeRepository,
            RecipeIngredientRepository ingredientRepository,
            RecipeStepRepository stepRepository,
            FamilyMemberRepository familyMemberRepository
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeIngredientResponse> listIngredients(
            String familyId,
            String recipeId,
            String userId,
            boolean includeDeleted
    ) {
        requireActiveRecipeForMember(familyId, recipeId, userId);
        List<RecipeIngredientEntity> ingredients = includeDeleted
                ? ingredientRepository.findByRecipe_IdOrderByPositionAsc(recipeId)
                : ingredientRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId);
        return ingredients
                .stream()
                .map(this::toIngredientResponse)
                .toList();
    }

    @Transactional
    public List<RecipeIngredientResponse> replaceIngredients(
            String familyId,
            String recipeId,
            String userId,
            RecipeIngredientListRequest request
    ) {
        requireEditor(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(familyId, recipeId);
        List<RecipeIngredientEntity> existing = ingredientRepository
                .findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId);
        existing.forEach(RecipeIngredientEntity::softDelete);

        List<RecipeIngredientEntity> replacements = createIngredients(recipe, request.items());
        ingredientRepository.saveAll(existing);
        List<RecipeIngredientEntity> saved = ingredientRepository.saveAll(replacements);
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return saved.stream().map(this::toIngredientResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeStepResponse> listSteps(
            String familyId,
            String recipeId,
            String userId,
            boolean includeDeleted
    ) {
        requireActiveRecipeForMember(familyId, recipeId, userId);
        List<RecipeStepEntity> steps = includeDeleted
                ? stepRepository.findByRecipe_IdOrderByPositionAsc(recipeId)
                : stepRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId);
        return steps
                .stream()
                .map(this::toStepResponse)
                .toList();
    }

    @Transactional
    public List<RecipeStepResponse> replaceSteps(
            String familyId,
            String recipeId,
            String userId,
            RecipeStepListRequest request
    ) {
        requireEditor(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(familyId, recipeId);
        List<RecipeStepEntity> existing = stepRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId);
        existing.forEach(RecipeStepEntity::softDelete);

        List<RecipeStepEntity> replacements = createSteps(recipe, request.items());
        stepRepository.saveAll(existing);
        List<RecipeStepEntity> saved = stepRepository.saveAll(replacements);
        recipe.markContentChanged();
        recipeRepository.save(recipe);
        return saved.stream().map(this::toStepResponse).toList();
    }

    private RecipeEntity requireActiveRecipeForMember(String familyId, String recipeId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
    }

    private RecipeEntity requireActiveRecipe(String familyId, String recipeId) {
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
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

    private List<RecipeIngredientEntity> createIngredients(
            RecipeEntity recipe,
            List<RecipeIngredientRequest> ingredients
    ) {
        return ingredients.stream()
                .map(new IngredientFactory(recipe)::create)
                .toList();
    }

    private List<RecipeStepEntity> createSteps(RecipeEntity recipe, List<RecipeStepRequest> steps) {
        return steps.stream()
                .map(new StepFactory(recipe)::create)
                .toList();
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

    private static final class IngredientFactory {
        private final RecipeEntity recipe;
        private int position = 1;

        private IngredientFactory(RecipeEntity recipe) {
            this.recipe = recipe;
        }

        private RecipeIngredientEntity create(RecipeIngredientRequest request) {
            return new RecipeIngredientEntity(
                    recipe,
                    position++,
                    request.name().trim(),
                    request.quantity(),
                    trimToNull(request.unit()),
                    trimToNull(request.note())
            );
        }
    }

    private static final class StepFactory {
        private final RecipeEntity recipe;
        private int position = 1;

        private StepFactory(RecipeEntity recipe) {
            this.recipe = recipe;
        }

        private RecipeStepEntity create(RecipeStepRequest request) {
            return new RecipeStepEntity(
                    recipe,
                    position++,
                    request.instruction().trim(),
                    request.timerMinutes()
            );
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
