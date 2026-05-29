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
        session.setTokens(response.accessToken(), response.refreshToken());
        // prefer nested family.id; fall back to legacy flat familyId field
        String fid = (response.family() != null) ? response.family().id() : response.familyId();
        session.setFamilyId(fid);
        if (response.user() != null) {
            session.setUserInfo(response.user().displayName(), response.user().email());
        }
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
