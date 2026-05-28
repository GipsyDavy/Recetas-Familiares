package org.gipsybuho.recetasfamiliares.ratings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRatingJpaRepository extends JpaRepository<RecipeRatingEntity, String> {

    List<RecipeRatingEntity> findByRecipe_IdAndDeletedFalseOrderByCreatedAtDesc(String recipeId);

    Optional<RecipeRatingEntity> findByRecipe_IdAndUser_IdAndDeletedFalse(
            String recipeId, String userId);

    Optional<RecipeRatingEntity> findByIdAndRecipe_IdAndRecipe_Family_IdAndDeletedFalse(
            String id, String recipeId, String familyId);
}
