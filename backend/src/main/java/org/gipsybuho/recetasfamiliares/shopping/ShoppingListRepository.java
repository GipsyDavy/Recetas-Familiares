package org.gipsybuho.recetasfamiliares.shopping;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListRepository extends JpaRepository<ShoppingListEntity, String> {

    Page<ShoppingListEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<ShoppingListEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<ShoppingListEntity> findByIdAndFamily_Id(String id, String familyId);

    List<ShoppingListEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);
}
