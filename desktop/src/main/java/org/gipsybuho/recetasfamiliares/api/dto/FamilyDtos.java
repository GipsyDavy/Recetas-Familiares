package org.gipsybuho.recetasfamiliares.api.dto;

public final class FamilyDtos {

    private FamilyDtos() {}

    /** Matches backend FamilyResponse: {id, name, role} */
    public record FamilyResponse(String id, String name, String role) {}
}
