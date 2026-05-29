package org.gipsybuho.recetasfamiliares.api.dto;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String email, String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record AuthUserInfo(String id, String email, String displayName) {}

    public record AuthFamilyInfo(String id, String name) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            AuthUserInfo user,
            AuthFamilyInfo family,
            // legacy flat fields kept for backwards compat with older backend versions
            String familyId
    ) {}
}
