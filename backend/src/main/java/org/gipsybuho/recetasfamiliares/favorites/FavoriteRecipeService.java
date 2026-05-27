package org.gipsybuho.recetasfamiliares.favorites;

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
public class FavoriteRecipeService {

    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final RecipeRepository recipeRepository;

    public FavoriteRecipeService(
            FavoriteRecipeRepository favoriteRecipeRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            RecipeRepository recipeRepository
    ) {
        this.favoriteRecipeRepository = favoriteRecipeRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<FavoriteRecipeResponse> listFavorites(String familyId, String userId, int page, int size) {
        requireMembership(familyId, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<FavoriteRecipeEntity> favorites = favoriteRecipeRepository.findByFamily_IdAndDeletedFalse(familyId, pageable);
        return new PageResponse<>(
                favorites.getContent().stream().map(this::toResponse).toList(),
                favorites.getNumber(),
                favorites.getSize(),
                favorites.getTotalElements(),
                favorites.getTotalPages()
        );
    }

    @Transactional
    public FavoriteRecipeResponse createFavorite(String familyId, String userId, CreateFavoriteRecipeRequest request) {
        requireEditor(familyId, userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
        RecipeEntity recipe = recipeRepository.findByIdAndFamily_IdAndDeletedFalse(request.recipeId(), familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe not found for favorite"));
        FavoriteRecipeEntity favorite = favoriteRecipeRepository.findByFamily_IdAndRecipe_Id(familyId, recipe.getId())
                .orElseGet(() -> new FavoriteRecipeEntity(family, recipe));
        favorite.restore();
        return toResponse(favoriteRecipeRepository.save(favorite));
    }

    @Transactional(readOnly = true)
    public FavoriteRecipeResponse getFavorite(String familyId, String favoriteId, String userId) {
        requireMembership(familyId, userId);
        return toResponse(requireActiveFavorite(familyId, favoriteId));
    }

    @Transactional
    public void deleteFavorite(String familyId, String favoriteId, String userId) {
        requireEditor(familyId, userId);
        FavoriteRecipeEntity favorite = requireActiveFavorite(familyId, favoriteId);
        favorite.softDelete();
        favoriteRecipeRepository.save(favorite);
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

    private FavoriteRecipeEntity requireActiveFavorite(String familyId, String favoriteId) {
        return favoriteRecipeRepository.findByIdAndFamily_IdAndDeletedFalse(favoriteId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite recipe not found"));
    }

    private FavoriteRecipeResponse toResponse(FavoriteRecipeEntity favorite) {
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
}
