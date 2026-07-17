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

    /** Creates an additional family; the caller becomes its OWNER (backend enforces role and limits). */
    public FamilyDtos.FamilyResponse createFamily(String name) throws ApiException {
        return api.post("api/v1/families",
                new FamilyDtos.CreateFamilyRequest(name),
                FamilyDtos.FamilyResponse.class);
    }

    /** Returns all members of the given family. */
    public FamilyDtos.FamilyMemberResponse[] loadMembers(String familyId) throws ApiException {
        FamilyDtos.FamilyMemberResponse[] result = api.get(
                "api/v1/families/" + familyId + "/members",
                FamilyDtos.FamilyMemberResponse[].class);
        return result != null ? result : new FamilyDtos.FamilyMemberResponse[0];
    }

    /** Returns aggregated stats for the given family. */
    public FamilyDtos.FamilyStatsResponse loadStats(String familyId) throws ApiException {
        return api.get("api/v1/families/" + familyId + "/stats", FamilyDtos.FamilyStatsResponse.class);
    }

    /** Returns recipe contribution ranking for active family members. */
    public FamilyDtos.UserRecipeRankingResponse[] loadRecipeRanking(String familyId) throws ApiException {
        FamilyDtos.UserRecipeRankingResponse[] result = api.get(
                "api/v1/families/" + familyId + "/recipe-rankings/users",
                FamilyDtos.UserRecipeRankingResponse[].class);
        return result != null ? result : new FamilyDtos.UserRecipeRankingResponse[0];
    }

    /** Changes the role of a family member (OWNER/ADMIN only). */
    public FamilyDtos.FamilyMemberResponse updateMemberRole(String familyId, String userId, String newRole) throws ApiException {
        return api.put("api/v1/families/" + familyId + "/members/" + userId + "/role",
                java.util.Map.of("role", newRole), FamilyDtos.FamilyMemberResponse.class);
    }

    /** Updates member profile data and optional password action (OWNER/ADMIN only). */
    public FamilyDtos.FamilyMemberResponse updateMember(
            String familyId,
            String userId,
            String displayName,
            String email,
            String passwordAction,
            String temporaryPassword
    ) throws ApiException {
        return api.put(
                "api/v1/families/" + familyId + "/members/" + userId,
                new FamilyDtos.UpdateFamilyMemberRequest(displayName, email, passwordAction, temporaryPassword),
                FamilyDtos.FamilyMemberResponse.class);
    }

    /** Invites an existing user or creates and adds a new user (OWNER/ADMIN only). */
    public void inviteMember(String familyId, String email, String displayName, String password, String role) throws ApiException {
        api.post("api/v1/families/" + familyId + "/members",
                new FamilyDtos.InviteMemberRequest(email, displayName, password, role),
                Void.class);
    }

    /** Removes (soft-deletes) a member from the family (OWNER/ADMIN only). */
    public void removeMember(String familyId, String userId) throws ApiException {
        api.delete("api/v1/families/" + familyId + "/members/" + userId);
    }

    /** Sube la imagen del grupo familiar (OWNER/ADMIN only). */
    public FamilyDtos.FamilyResponse uploadAvatar(String familyId, java.io.File file) throws ApiException {
        return api.postMultipart("api/v1/families/" + familyId + "/avatar", file, "file",
                FamilyDtos.FamilyResponse.class);
    }

    /**
     * Calls GET /api/v1/families, reads the role for the active family, and
     * persists it in the session. Defaults to MEMBER on any error (fail-safe).
     */
    public void detectAndSaveRole() {
        try {
            FamilyDtos.FamilyResponse[] families = loadMyFamilies();
            String activeFamilyId = session.getFamilyId();
            FamilyRole role = FamilyRole.MEMBER;
            FamilyDtos.FamilyResponse active = null;
            for (FamilyDtos.FamilyResponse family : families) {
                if (activeFamilyId != null && activeFamilyId.equals(family.id())) {
                    active = family;
                    break;
                }
            }
            if (active == null && families.length > 0) {
                active = families[0];
                if (activeFamilyId == null || activeFamilyId.isBlank()) {
                    session.setFamilyId(active.id());
                }
            }
            if (active != null && active.role() != null) {
                try {
                    role = FamilyRole.valueOf(active.role().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // Unknown role string — default to MEMBER
                }
            }
            session.setFamilyRole(role);
        } catch (Exception ex) {
            if (session.getFamilyRole() == null) {
                session.setFamilyRole(FamilyRole.MEMBER);
            }
        }
    }
}
