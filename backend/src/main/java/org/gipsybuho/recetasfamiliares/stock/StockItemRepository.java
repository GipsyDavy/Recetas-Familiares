package org.gipsybuho.recetasfamiliares.stock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockItemRepository extends JpaRepository<StockItemEntity, String> {

    Page<StockItemEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<StockItemEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<StockItemEntity> findByIdAndFamily_Id(String id, String familyId);

    List<StockItemEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);

    long countByFamily_IdAndDeletedFalse(String familyId);
}
