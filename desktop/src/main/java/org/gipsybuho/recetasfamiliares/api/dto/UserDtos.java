package org.gipsybuho.recetasfamiliares.api.dto;

public final class UserDtos {
    private UserDtos() {}
    public record UpdateDisplayNameRequest(String displayName) {}
    public record UserResponse(String id, String email, String displayName, String avatarUrl) {}
}
