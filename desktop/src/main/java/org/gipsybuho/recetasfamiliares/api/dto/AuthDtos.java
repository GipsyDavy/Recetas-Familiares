package org.gipsybuho.recetasfamiliares.api.dto;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String email, String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String familyId,
            String userId,
            String role
    ) {}
}
