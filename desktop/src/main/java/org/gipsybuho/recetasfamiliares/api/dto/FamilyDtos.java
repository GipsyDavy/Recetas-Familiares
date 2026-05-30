package org.gipsybuho.recetasfamiliares.api.dto;

public final class FamilyDtos {

    private FamilyDtos() {}

    /** Matches backend FamilyResponse: {id, name, role} */
    public record FamilyResponse(String id, String name, String role) {}

    /** Matches backend FamilyMemberResponse: {userId, displayName, email, avatarUrl, role} */
    public record FamilyMemberResponse(String userId, String displayName, String email, String avatarUrl, String role) {}
}
