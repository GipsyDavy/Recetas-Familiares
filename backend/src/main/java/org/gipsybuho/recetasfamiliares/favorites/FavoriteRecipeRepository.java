package org.gipsybuho.recetasfamiliares.favorites;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRecipeRepository extends JpaRepository<FavoriteRecipeEntity, String> {

    Page<FavoriteRecipeEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<FavoriteRecipeEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<FavoriteRecipeEntity> findByIdAndFamily_Id(String id, String familyId);

    Optional<FavoriteRecipeEntity> findByFamily_IdAndRecipe_Id(String familyId, String recipeId);

    List<FavoriteRecipeEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);
}
