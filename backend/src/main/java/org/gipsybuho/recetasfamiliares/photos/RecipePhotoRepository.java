package org.gipsybuho.recetasfamiliares.photos;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipePhotoRepository extends JpaRepository<RecipePhotoEntity, String> {

    List<RecipePhotoEntity> findByRecipe_IdAndDeletedFalseOrderByPositionAsc(String recipeId);

    List<RecipePhotoEntity> findByRecipe_IdOrderByPositionAsc(String recipeId);

    Optional<RecipePhotoEntity> findByIdAndRecipe_IdAndRecipe_Family_IdAndDeletedFalse(
            String id,
            String recipeId,
            String familyId
    );

    Optional<RecipePhotoEntity> findByIdAndRecipe_Family_Id(String id, String familyId);

    List<RecipePhotoEntity> findByRecipe_Family_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);

    List<RecipePhotoEntity> findByRecipe_Family_IdAndUpdatedAtAfter(String familyId, Instant since, Pageable pageable);

    @Query("""
            SELECT p.recipe.family.id
            FROM RecipePhotoEntity p
            WHERE p.deleted = false
              AND p.storagePath = :storagePath
            """)
    List<String> findOwningFamilyIdsByStoragePath(@Param("storagePath") String storagePath);

    /**
     * Candidatas a portada de varias recetas en una sola consulta. El filtro por familia
     * no es redundante con el IN: impide que un id de receta ajena devuelva una URL de
     * otra familia. Va sobre ix_recipe_photos_recipe_active (recipe_id, deleted, position).
     */
    @Query("""
            SELECT p.recipe.id AS recipeId,
                   p.thumbnailUrl AS thumbnailUrl,
                   p.url AS url
            FROM RecipePhotoEntity p
            WHERE p.recipe.family.id = :familyId
              AND p.recipe.id IN :recipeIds
              AND p.deleted = false
            ORDER BY p.recipe.id ASC, p.position ASC
            """)
    List<RecipeCoverProjection> findCoverCandidates(
            @Param("familyId") String familyId,
            @Param("recipeIds") Collection<String> recipeIds
    );
}
