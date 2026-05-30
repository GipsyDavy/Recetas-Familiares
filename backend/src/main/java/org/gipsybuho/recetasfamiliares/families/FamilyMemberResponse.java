package org.gipsybuho.recetasfamiliares.families;

public record FamilyMemberResponse(
        String userId,
        String displayName,
        String email,
        String avatarUrl,
        String role
) {
}
