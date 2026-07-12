package org.gipsybuho.recetasfamiliares.ratings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRatingJpaRepository extends JpaRepository<RecipeRatingEntity, String> {

    List<RecipeRatingEntity> findByRecipe_IdAndDeletedFalseOrderByCreatedAtDesc(String recipeId);

    Optional<RecipeRatingEntity> findByRecipe_IdAndUser_IdAndDeletedFalse(
            String recipeId, String userId);

    Optional<RecipeRatingEntity> findByIdAndRecipe_IdAndRecipe_Family_IdAndDeletedFalse(
            String id, String recipeId, String familyId);

    @Query("""
            SELECT recipe.createdByUser.id AS userId,
                   COUNT(rating.id) AS ratingsReceived,
                   AVG(rating.stars) AS averageStars,
                   SUM(rating.stars) AS starsReceived
            FROM RecipeRatingEntity rating
            JOIN rating.recipe recipe
            WHERE rating.family.id = :familyId
              AND rating.deleted = false
              AND recipe.deleted = false
              AND recipe.createdByUser IS NOT NULL
              AND rating.user.id <> recipe.createdByUser.id
            GROUP BY recipe.createdByUser.id
            """)
    List<CreatorRatingStats> aggregateReceivedRatingsByRecipeCreator(@Param("familyId") String familyId);

    interface CreatorRatingStats {
        String getUserId();
        long getRatingsReceived();
        Double getAverageStars();
        Long getStarsReceived();
    }
}
