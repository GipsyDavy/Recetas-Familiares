package org.gipsybuho.recetasfamiliares.families;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMemberRepository extends JpaRepository<FamilyMemberEntity, String> {

    Optional<FamilyMemberEntity> findFirstByUser_IdAndDeletedFalse(String userId);

    List<FamilyMemberEntity> findByUser_IdAndDeletedFalse(String userId);

    boolean existsByFamily_IdAndUser_IdAndDeletedFalse(String familyId, String userId);

    boolean existsByFamily_IdAndUser_IdAndRoleInAndDeletedFalse(
            String familyId,
            String userId,
            Collection<FamilyRole> roles
    );

    long countByFamily_IdAndDeletedFalse(String familyId);

    @Query("SELECT m FROM FamilyMemberEntity m JOIN FETCH m.user WHERE m.family.id = :familyId AND m.deleted = false ORDER BY m.createdAt ASC")
    List<FamilyMemberEntity> findMembersWithUserByFamilyId(@Param("familyId") String familyId);

    @Query("SELECT m FROM FamilyMemberEntity m JOIN FETCH m.user WHERE m.family.id = :familyId AND m.user.id = :userId AND m.deleted = false")
    Optional<FamilyMemberEntity> findMemberWithUserByFamilyIdAndUserId(@Param("familyId") String familyId, @Param("userId") String userId);
}
