package org.gipsybuho.recetasfamiliares.families;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, String> {

    Optional<FamilyMemberEntity> findFirstByUser_IdAndDeletedFalse(String userId);

    List<FamilyMemberEntity> findByUser_IdAndDeletedFalse(String userId);

    boolean existsByFamily_IdAndUser_IdAndDeletedFalse(String familyId, String userId);
}
