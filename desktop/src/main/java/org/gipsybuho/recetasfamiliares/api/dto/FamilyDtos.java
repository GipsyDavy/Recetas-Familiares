package org.gipsybuho.recetasfamiliares.api.dto;

public final class FamilyDtos {

    private FamilyDtos() {}

    /** Matches backend FamilyResponse: {id, name, role, avatarUrl} */
    public record FamilyResponse(String id, String name, String role, String avatarUrl) {}

    /** Matches backend FamilyMemberResponse: {userId, displayName, email, avatarUrl, role} */
    public record FamilyMemberResponse(String userId, String displayName, String email, String avatarUrl, String role) {}

    /** Matches backend InviteMemberRequest: existing-user invite or new-user creation. */
    public record InviteMemberRequest(String email, String displayName, String password, String role) {}

    /** Matches backend FamilyStatsResponse: {totalRecipes, totalMembers, totalStockItems, lastActivityAt} */
    public record FamilyStatsResponse(long totalRecipes, long totalMembers, long totalStockItems, String lastActivityAt) {}
}
