package org.gipsybuho.recetasfamiliares.auth;

public record AuthResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        AuthUserResponse user,
        AuthFamilyResponse family
) {
}
