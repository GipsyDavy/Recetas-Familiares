package org.gipsybuho.recetasfamiliares.activity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSectionLastSeenRepository
        extends JpaRepository<UserSectionLastSeenEntity, UserSectionLastSeenEntity.Key> {

    Optional<UserSectionLastSeenEntity> findByUserIdAndFamilyIdAndSection(
            String userId, String familyId, FamilySection section);
}
