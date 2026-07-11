package org.gipsybuho.recetasfamiliares.data.repository;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.AuthDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

public class AuthRepository {

    private final ApiClient api;
    private final AppSession session;

    public AuthRepository(ApiClient api, AppSession session) {
        this.api = api;
        this.session = session;
    }

    /** Authenticates and stores tokens in session. Throws ApiException on failure. */
    public void login(String email, String password) throws ApiException {
        var request = new AuthDtos.LoginRequest(email, password);
        AuthDtos.AuthResponse response = api.postAuth("api/v1/auth/login", request, AuthDtos.AuthResponse.class);
        applyAuthResponse(response);
    }

    /** Registers a new owner family account and stores tokens in session. */
    public void register(String email, String displayName, String password, String familyName) throws ApiException {
        var request = new AuthDtos.RegisterRequest(email, displayName, password, familyName);
        AuthDtos.AuthResponse response = api.postAuth("api/v1/auth/register", request, AuthDtos.AuthResponse.class);
        applyAuthResponse(response);
    }

    private void applyAuthResponse(AuthDtos.AuthResponse response) {
        session.setTokens(response.accessToken(), response.refreshToken());
        // prefer nested family.id; fall back to legacy flat familyId field
        String fid = (response.family() != null) ? response.family().id() : response.familyId();
        session.setFamilyId(fid);
        if (response.user() != null) {
            session.setUserInfo(response.user().displayName(), response.user().email());
            session.setUserId(response.user().id());
        }
    }

    /**
     * Solicita el email de recuperacion. El backend responde 202 exista o no la
     * cuenta (anti-enumeracion), asi que el exito aqui solo significa "aceptado".
     */
    public void requestPasswordReset(String email) throws ApiException {
        api.postAuth("api/v1/auth/password-reset/request",
                new AuthDtos.PasswordResetRequest(email.trim()), Void.class);
    }

    /** Cambia la password con el token recibido por email. Revoca las sesiones activas. */
    public void confirmPasswordReset(String token, String newPassword) throws ApiException {
        api.postAuth("api/v1/auth/password-reset/confirm",
                new AuthDtos.ConfirmPasswordResetRequest(token.trim(), newPassword), Void.class);
    }

    public void requestEmailVerification(String email) throws ApiException {
        api.postAuth("api/v1/auth/email-verification/request",
                new AuthDtos.EmailVerificationRequest(email.trim()), Void.class);
    }

    public void confirmEmailVerification(String token) throws ApiException {
        api.postAuth("api/v1/auth/email-verification/confirm",
                new AuthDtos.ConfirmEmailVerificationRequest(token.trim()), Void.class);
    }

    /**
     * Borra (anonimiza) la cuenta autenticada. Exige la password actual.
     * Si el backend acepta, limpia la sesion local; el llamador vuelve al login.
     */
    public void deleteAccount(String password) throws ApiException {
        api.deleteWithBody("api/v1/auth/account", new AuthDtos.DeleteAccountRequest(password));
        session.clear();
    }

    public void logout() {
        try {
            api.post("api/v1/auth/logout", "{}", Void.class);
        } catch (ApiException ignored) {
            // Best-effort logout — clear session regardless
        } finally {
            session.clear();
        }
    }
}
