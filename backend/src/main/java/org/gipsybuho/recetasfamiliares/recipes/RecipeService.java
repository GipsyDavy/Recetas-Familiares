package org.gipsybuho.recetasfamiliares.recipes;

import java.util.List;

import org.gipsybuho.recetasfamiliares.common.api.PageResponse;
import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoEntity;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final RecipePhotoRepository photoRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public RecipeService(
            RecipeRepository recipeRepository,
            RecipeIngredientRepository ingredientRepository,
            RecipeStepRepository stepRepository,
            RecipePhotoRepository photoRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeResponse> listRecipes(String familyId, String userId, int page, int size) {
        requireMembership(familyId, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<RecipeEntity> recipes = recipeRepository.findByFamily_IdAndDeletedFalse(familyId, pageable);
        return new PageResponse<>(
                recipes.getContent().stream().map(this::toResponse).toList(),
                recipes.getNumber(),
                recipes.getSize(),
                recipes.getTotalElements(),
                recipes.getTotalPages()
        );
    }

    @Transactional
    public RecipeResponse createRecipe(String familyId, String userId, CreateRecipeRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        RecipeEntity recipe = new RecipeEntity(
                family,
                request.title().trim(),
                request.description() == null ? null : request.description().trim(),
                request.servings(),
                request.prepMinutes(),
                request.cookMinutes(),
                request.difficulty()
        );
        return toResponse(recipeRepository.save(recipe));
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(String familyId, String recipeId, String userId) {
        requireMembership(familyId, userId);
        return toResponse(requireActiveRecipe(familyId, recipeId));
    }

    @Transactional
    public RecipeResponse updateRecipe(String familyId, String recipeId, String userId, UpdateRecipeRequest request) {
        requireEditor(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(familyId, recipeId);
        recipe.update(
                request.title().trim(),
                request.description() == null ? null : request.description().trim(),
                request.servings(),
                request.prepMinutes(),
                request.cookMinutes(),
                request.difficulty()
        );
        return toResponse(recipeRepository.save(recipe));
    }

    @Transactional
    public void deleteRecipe(String familyId, String recipeId, String userId) {
        requireEditor(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(familyId, recipeId);
        ingredientRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipeIngredientEntity::softDelete);
        stepRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipeStepEntity::softDelete);
        photoRepository.findByRecipe_IdAndDeletedFalseOrderByPositionAsc(recipeId)
                .forEach(RecipePhotoEntity::softDelete);
        recipe.softDelete();
        recipeRepository.save(recipe);
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

    private RecipeEntity requireActiveRecipe(String familyId, String recipeId) {
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
    }

    private RecipeResponse toResponse(RecipeEntity recipe) {
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
}
