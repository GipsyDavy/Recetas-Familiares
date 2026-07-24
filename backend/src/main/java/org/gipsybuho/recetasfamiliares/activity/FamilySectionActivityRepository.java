package org.gipsybuho.recetasfamiliares.activity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilySectionActivityRepository
        extends JpaRepository<FamilySectionActivityEntity, FamilySectionActivityEntity.Key> {

    Optional<FamilySectionActivityEntity> findByFamilyIdAndSection(String familyId, FamilySection section);
}
