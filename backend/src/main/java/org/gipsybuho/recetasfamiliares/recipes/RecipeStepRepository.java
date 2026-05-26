package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeStepRepository extends JpaRepository<RecipeStepEntity, String> {

    List<RecipeStepEntity> findByRecipe_IdAndDeletedFalseOrderByPositionAsc(String recipeId);

    List<RecipeStepEntity> findByRecipe_IdOrderByPositionAsc(String recipeId);

    Optional<RecipeStepEntity> findByIdAndRecipe_Family_Id(String id, String familyId);

    List<RecipeStepEntity> findByRecipe_Family_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);
}
