package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredientEntity, String> {

    List<RecipeIngredientEntity> findByRecipe_IdAndDeletedFalseOrderByPositionAsc(String recipeId);

    List<RecipeIngredientEntity> findByRecipe_IdOrderByPositionAsc(String recipeId);

    Optional<RecipeIngredientEntity> findByIdAndRecipe_Family_Id(String id, String familyId);

    List<RecipeIngredientEntity> findByRecipe_Family_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(
            String familyId,
            Instant since
    );
}
