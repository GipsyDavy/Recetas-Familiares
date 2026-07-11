package org.gipsybuho.recetasfamiliares.api.dto;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String email, String password) {}

    public record RegisterRequest(String email, String displayName, String password, String familyName) {}

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

    public record PasswordResetRequest(String email) {}

    public record ConfirmPasswordResetRequest(String token, String newPassword) {}

    public record EmailVerificationRequest(String email) {}

    public record ConfirmEmailVerificationRequest(String token) {}

    public record DeleteAccountRequest(String password) {}
}
