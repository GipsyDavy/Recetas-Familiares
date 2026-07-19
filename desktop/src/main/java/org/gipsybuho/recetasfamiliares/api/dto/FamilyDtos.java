package org.gipsybuho.recetasfamiliares.api.dto;

public final class FamilyDtos {

    private FamilyDtos() {}

    /** Matches backend FamilyResponse: {id, name, role, avatarUrl} */
    public record FamilyResponse(String id, String name, String role, String avatarUrl) {}

    /** Matches backend CreateFamilyRequest: {name}. */
    public record CreateFamilyRequest(String name) {}

    /** Matches backend FamilyMemberResponse: {userId, displayName, email, avatarUrl, role} */
    public record FamilyMemberResponse(String userId, String displayName, String email, String avatarUrl, String role) {}

    /** Matches backend InviteMemberRequest: existing-user invite or new-user creation. */
    public record InviteMemberRequest(String email, String displayName, String password, String role) {}

    /** Matches backend UpdateFamilyMemberRequest. */
    public record UpdateFamilyMemberRequest(
            String displayName,
            String email,
            String passwordAction,
            String temporaryPassword
    ) {}

    /** Matches backend FamilyStatsResponse: {totalRecipes, totalMembers, totalStockItems, lastActivityAt} */
    public record FamilyStatsResponse(long totalRecipes, long totalMembers, long totalStockItems, String lastActivityAt) {}

    /** Snapshot de miembros conectados ahora mismo (WebSocket activo). */
    public record PresenceResponse(java.util.List<String> onlineUserIds) {}

    /** Matches backend UserRecipeRankingResponse. */
    public record UserRecipeRankingResponse(
            int rank,
            String userId,
            String displayName,
            String role,
            long recipesCreated,
            long ratingsReceived,
            Double averageStars,
            long score
    ) {}
}
