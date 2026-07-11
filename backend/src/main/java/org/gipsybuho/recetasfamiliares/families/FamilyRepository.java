package org.gipsybuho.recetasfamiliares.families;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyRepository extends JpaRepository<FamilyEntity, String> {

    /** Familias cuyo avatar apunta al archivo local dado (URL absoluta o relativa). */
    @Query("""
            SELECT f.id
            FROM FamilyEntity f
            WHERE f.deleted = false
              AND (f.avatarUrl = :absoluteUrl OR f.avatarUrl = :relativePath)
            """)
    List<String> findIdsByAvatarLocalUrl(
            @Param("absoluteUrl") String absoluteUrl,
            @Param("relativePath") String relativePath
    );
}
