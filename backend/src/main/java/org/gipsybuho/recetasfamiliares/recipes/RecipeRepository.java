package org.gipsybuho.recetasfamiliares.recipes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<RecipeEntity, String> {

    Page<RecipeEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<RecipeEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<RecipeEntity> findByIdAndFamily_Id(String id, String familyId);

    List<RecipeEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);
}
