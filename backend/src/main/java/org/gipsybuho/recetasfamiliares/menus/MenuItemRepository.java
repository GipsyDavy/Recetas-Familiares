package org.gipsybuho.recetasfamiliares.menus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, String> {

    Page<MenuItemEntity> findByFamily_IdAndPlannedDateBetweenAndDeletedFalse(
            String familyId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    List<MenuItemEntity> findByFamily_IdAndPlannedDateBetweenAndDeletedFalseOrderByPlannedDateAscMealTypeAsc(
            String familyId,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<MenuItemEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<MenuItemEntity> findByIdAndFamily_Id(String id, String familyId);

    List<MenuItemEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);

    List<MenuItemEntity> findByFamily_IdAndUpdatedAtAfter(String familyId, Instant since, Pageable pageable);

    @Query("SELECT MAX(m.updatedAt) FROM MenuItemEntity m WHERE m.family.id = :familyId")
    Instant findLastActivityAt(@Param("familyId") String familyId);
}
