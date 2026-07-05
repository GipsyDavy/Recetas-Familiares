package org.gipsybuho.recetasfamiliares.stock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItemEntity, String> {

    Page<StockItemEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<StockItemEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<StockItemEntity> findByIdAndFamily_Id(String id, String familyId);

    List<StockItemEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);

    List<StockItemEntity> findByFamily_IdAndUpdatedAtAfter(String familyId, Instant since, Pageable pageable);

    long countByFamily_IdAndDeletedFalse(String familyId);

    @Query("SELECT MAX(s.updatedAt) FROM StockItemEntity s WHERE s.family.id = :familyId")
    Instant findLastActivityAt(@Param("familyId") String familyId);
}
