package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.core.FamilyRole;

public class FamilyRepository {

    private final ApiClient api;
    private final AppSession session;

    public FamilyRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    /** Returns all families the current user belongs to, each including the user's role. */
    public FamilyDtos.FamilyResponse[] loadMyFamilies() throws ApiException {
        FamilyDtos.FamilyResponse[] result = api.get("api/v1/families", FamilyDtos.FamilyResponse[].class);
        return result != null ? result : new FamilyDtos.FamilyResponse[0];
    }

    /**
     * Calls GET /api/v1/families, reads the role from the first family in the list,
     * and persists it in the session. Defaults to MEMBER on any error (fail-safe).
     */
    public void detectAndSaveRole() {
        try {
            FamilyDtos.FamilyResponse[] families = loadMyFamilies();
            FamilyRole role = FamilyRole.MEMBER;
            if (families.length > 0 && families[0].role() != null) {
                try {
                    role = FamilyRole.valueOf(families[0].role().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // Unknown role string — default to MEMBER
                }
            }
            session.setFamilyRole(role);
        } catch (Exception ex) {
            session.setFamilyRole(FamilyRole.MEMBER);
        }
    }
}
