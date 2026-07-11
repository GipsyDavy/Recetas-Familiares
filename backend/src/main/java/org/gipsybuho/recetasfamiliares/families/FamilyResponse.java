package org.gipsybuho.recetasfamiliares.families;

public record FamilyResponse(
        String id,
        String name,
        FamilyRole role,
        String avatarUrl
) {
}
