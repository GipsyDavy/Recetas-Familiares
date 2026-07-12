package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<RecipeEntity, String> {

    @EntityGraph(attributePaths = "createdByUser")
    Page<RecipeEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    @EntityGraph(attributePaths = "createdByUser")
    Optional<RecipeEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    @EntityGraph(attributePaths = "createdByUser")
    Optional<RecipeEntity> findByIdAndFamily_Id(String id, String familyId);

    List<RecipeEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);

    @EntityGraph(attributePaths = "createdByUser")
    List<RecipeEntity> findByFamily_IdAndUpdatedAtAfter(String familyId, Instant since, Pageable pageable);

    long countByFamily_IdAndDeletedFalse(String familyId);

    @Query("""
            SELECT r.createdByUser.id AS userId, COUNT(r.id) AS recipeCount
            FROM RecipeEntity r
            WHERE r.family.id = :familyId
              AND r.deleted = false
              AND r.createdByUser IS NOT NULL
            GROUP BY r.createdByUser.id
            """)
    List<CreatorRecipeCount> countActiveRecipesByCreator(@Param("familyId") String familyId);

    @Query("SELECT MAX(r.updatedAt) FROM RecipeEntity r WHERE r.family.id = :familyId")
    Instant findLastActivityAt(@Param("familyId") String familyId);

    interface CreatorRecipeCount {
        String getUserId();
        long getRecipeCount();
    }
}
