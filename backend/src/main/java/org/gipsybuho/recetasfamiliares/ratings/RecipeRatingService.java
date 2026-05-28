package org.gipsybuho.recetasfamiliares.ratings;

import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecipeRatingService {

    private final RecipeRatingJpaRepository ratingRepository;
    private final RecipeRepository recipeRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

    public RecipeRatingService(
            RecipeRatingJpaRepository ratingRepository,
            RecipeRepository recipeRepository,
            FamilyMemberRepository familyMemberRepository,
            FamilyRepository familyRepository,
            UserRepository userRepository
    ) {
        this.ratingRepository = ratingRepository;
        this.recipeRepository = recipeRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.familyRepository = familyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeRatingResponse> listRatings(String familyId, String recipeId, String userId) {
        requireFamilyMember(familyId, userId);
        requireActiveRecipe(recipeId, familyId);
        return ratingRepository.findByRecipe_IdAndDeletedFalseOrderByCreatedAtDesc(recipeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecipeRatingResponse createRating(String familyId, String recipeId, String userId,
                                              CreateRatingRequest request) {
        requireFamilyMember(familyId, userId);
        RecipeEntity recipe = requireActiveRecipe(recipeId, familyId);
        UserEntity user = requireUser(userId);
        FamilyEntity family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));

        ratingRepository.findByRecipe_IdAndUser_IdAndDeletedFalse(recipeId, userId)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ya has valorado esta receta");
                });

        String trimmedComment = trimToNull(request.comment());
        RecipeRatingEntity rating = new RecipeRatingEntity(recipe, family, user,
                request.stars(), trimmedComment);
        return toResponse(ratingRepository.save(rating));
    }

    @Transactional
    public RecipeRatingResponse updateRating(String familyId, String recipeId, String ratingId,
                                              String userId, UpdateRatingRequest request) {
        requireFamilyMember(familyId, userId);
        RecipeRatingEntity rating = requireOwnRating(ratingId, recipeId, familyId, userId);
        rating.update(request.stars(), trimToNull(request.comment()));
        return toResponse(ratingRepository.save(rating));
    }

    @Transactional
    public void deleteRating(String familyId, String recipeId, String ratingId, String userId) {
        requireFamilyMember(familyId, userId);
        RecipeRatingEntity rating = requireOwnRating(ratingId, recipeId, familyId, userId);
        rating.softDelete();
        ratingRepository.save(rating);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RecipeEntity requireActiveRecipe(String recipeId, String familyId) {
        return recipeRepository.findByIdAndFamily_IdAndDeletedFalse(recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recipe not found"));
    }

    private void requireFamilyMember(String familyId, String userId) {
        if (!familyMemberRepository.existsByFamily_IdAndUser_IdAndDeletedFalse(familyId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Family access denied");
        }
    }

    private UserEntity requireUser(String userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private RecipeRatingEntity requireOwnRating(String ratingId, String recipeId,
                                                 String familyId, String userId) {
        RecipeRatingEntity rating = ratingRepository
                .findByIdAndRecipe_IdAndRecipe_Family_IdAndDeletedFalse(ratingId, recipeId, familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rating not found"));
        if (!rating.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot modify another user's rating");
        }
        return rating;
    }

    private RecipeRatingResponse toResponse(RecipeRatingEntity r) {
        return new RecipeRatingResponse(
                r.getId(), r.getRecipeId(), r.getUserId(), r.getUserDisplayName(),
                r.getStars(), r.getComment(),
                r.getCreatedAt().toString(), r.getUpdatedAt().toString(),
                r.getSyncVersion(), r.isDeleted()
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
