package org.gipsybuho.recetasfamiliares.core;

import org.gipsybuho.recetasfamiliares.api.ApiClient;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.AuthDtos;
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthRepositoryTest {

    private Preferences prefs;
    private AppSession session;
    private FakeApiClient api;
    private AuthRepository repository;

    @BeforeEach
    void setUp() throws BackingStoreException {
        prefs = Preferences.userRoot().node("recetas-familiares-auth-test-" + UUID.randomUUID());
        prefs.clear();
        session = new AppSession(prefs);
        api = new FakeApiClient(session);
        repository = new AuthRepository(api, session);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        if (prefs != null && prefs.nodeExists("")) {
            prefs.removeNode();
            prefs.flush();
        }
    }

    @Test
    void registerGuardaSesionYPrefiereFamiliaAnidada() {
        api.authResponse = new AuthDtos.AuthResponse(
                "access-token",
                "refresh-token",
                new AuthDtos.AuthUserInfo("u1", "emma@example.test", "Emma"),
                new AuthDtos.AuthFamilyInfo("fam-nested", "Familia"),
                "fam-legacy"
        );

        repository.register("emma@example.test", "Emma", "secret-pass", "Familia");

        assertEquals("api/v1/auth/register", api.lastAuthPath);
        AuthDtos.RegisterRequest request = assertInstanceOf(AuthDtos.RegisterRequest.class, api.lastAuthBody);
        assertEquals("emma@example.test", request.email());
        assertEquals("Emma", request.displayName());
        assertEquals("secret-pass", request.password());
        assertEquals("Familia", request.familyName());
        assertEquals("access-token", session.getAccessToken());
        assertEquals("refresh-token", session.getRefreshToken());
        assertEquals("fam-nested", session.getFamilyId());
        assertEquals("Emma", session.getDisplayName());
        assertEquals("emma@example.test", session.getEmail());
    }

    @Test
    void loginUsaFamilyIdLegacySiNoHayFamiliaAnidada() {
        api.authResponse = new AuthDtos.AuthResponse(
                "access-token",
                "refresh-token",
                new AuthDtos.AuthUserInfo("u1", "emma@example.test", "Emma"),
                null,
                "fam-legacy"
        );

        repository.login("emma@example.test", "secret-pass");

        assertEquals("api/v1/auth/login", api.lastAuthPath);
        AuthDtos.LoginRequest request = assertInstanceOf(AuthDtos.LoginRequest.class, api.lastAuthBody);
        assertEquals("emma@example.test", request.email());
        assertEquals("secret-pass", request.password());
        assertEquals("fam-legacy", session.getFamilyId());
        assertEquals("Emma", session.getDisplayName());
    }

    @Test
    void logoutLimpiaSesionAunqueApiFalle() {
        session.setTokens("access-token", "refresh-token");
        session.setFamilyId("fam-1");
        session.setUserInfo("Emma", "emma@example.test");
        api.throwOnPost = true;

        repository.logout();

        assertEquals("api/v1/auth/logout", api.lastPostPath);
        assertFalse(session.isLoggedIn());
        assertNull(session.getAccessToken());
        assertNull(session.getRefreshToken());
        assertNull(session.getFamilyId());
        assertNull(session.getDisplayName());
        assertNull(session.getEmail());
    }

    private static final class FakeApiClient extends ApiClient {
        private String lastAuthPath;
        private Object lastAuthBody;
        private AuthDtos.AuthResponse authResponse;
        private String lastPostPath;
        private boolean throwOnPost;

        FakeApiClient(AppSession session) {
            super(session);
        }

        @Override
        public <T> T postAuth(String path, Object body, Class<T> responseType) throws ApiException {
            this.lastAuthPath = path;
            this.lastAuthBody = body;
            return responseType.cast(authResponse);
        }

        @Override
        public <T> T post(String path, Object body, Class<T> responseType) throws ApiException {
            this.lastPostPath = path;
            if (throwOnPost) {
                throw new ApiException(500, "fallo simulado");
            }
            return null;
        }
    }
}
