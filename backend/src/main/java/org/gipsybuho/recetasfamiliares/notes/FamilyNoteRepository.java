package org.gipsybuho.recetasfamiliares.notes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyNoteRepository extends JpaRepository<FamilyNoteEntity, String> {

    Page<FamilyNoteEntity> findByFamily_IdAndDeletedFalse(String familyId, Pageable pageable);

    Optional<FamilyNoteEntity> findByIdAndFamily_IdAndDeletedFalse(String id, String familyId);

    Optional<FamilyNoteEntity> findByIdAndFamily_Id(String id, String familyId);

    List<FamilyNoteEntity> findByFamily_IdAndUpdatedAtAfterOrderByUpdatedAtAsc(String familyId, Instant since);
}
