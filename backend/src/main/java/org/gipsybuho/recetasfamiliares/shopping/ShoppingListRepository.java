package org.gipsybuho.recetasfamiliares.shopping;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShoppingListRepository extends JpaRepository<ShoppingListEntity, String> {

    Page<ShoppingListEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<ShoppingListEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<ShoppingListEntity> findByIdAndFamily_Id(String id, String familyId);

    List<ShoppingListEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);

    List<ShoppingListEntity> findByFamily_IdAndUpdatedAtAfter(String familyId, Instant since, Pageable pageable);

    @Query("SELECT MAX(l.updatedAt) FROM ShoppingListEntity l WHERE l.family.id = :familyId")
    Instant findLastActivityAt(@Param("familyId") String familyId);
}
