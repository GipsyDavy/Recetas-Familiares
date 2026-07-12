package org.gipsybuho.recetasfamiliares.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSessionTest {

    private Preferences prefs;

    @BeforeEach
    void setUp() throws BackingStoreException {
        prefs = Preferences.userRoot().node("recetas-familiares-test-" + UUID.randomUUID());
        prefs.clear();
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        if (prefs != null && prefs.nodeExists("")) {
            prefs.removeNode();
            prefs.flush();
        }
    }

    @Test
    void persisteSesionYClearEliminaTodosLosCampos() {
        AppSession session = new AppSession(prefs);
        session.setTokens("access-token", "refresh-token");
        session.setFamilyId("fam-1");
        session.setLastSyncTime("2026-07-05T10:00:00Z");
        session.setUserInfo("Emma", "emma@example.test");
        session.setFamilyRole(FamilyRole.OWNER);
        session.setAvatarUrl("http://localhost/avatar.jpg");

        AppSession reloaded = new AppSession(prefs);

        assertTrue(reloaded.isLoggedIn());
        assertTrue(reloaded.isAdmin());
        assertEquals("access-token", reloaded.getAccessToken());
        assertEquals("refresh-token", reloaded.getRefreshToken());
        assertEquals("fam-1", reloaded.getFamilyId());
        assertEquals("2026-07-05T10:00:00Z", reloaded.getLastSyncTime());
        assertEquals("Emma", reloaded.getDisplayName());
        assertEquals("emma@example.test", reloaded.getEmail());
        assertEquals("http://localhost/avatar.jpg", reloaded.getAvatarUrl());

        reloaded.clear();
        AppSession cleared = new AppSession(prefs);

        assertFalse(cleared.isLoggedIn());
        assertFalse(cleared.isAdmin());
        assertNull(cleared.getAccessToken());
        assertNull(cleared.getRefreshToken());
        assertNull(cleared.getFamilyId());
        assertNull(cleared.getLastSyncTime());
        assertNull(cleared.getDisplayName());
        assertNull(cleared.getEmail());
        assertNull(cleared.getFamilyRole());
        assertNull(cleared.getAvatarUrl());
    }

    @Test
    void rolDesconocidoCaeAMemberSinPrivilegiosAdmin() {
        prefs.put("familyRole", "SUPER_OWNER");

        AppSession session = new AppSession(prefs);

        assertEquals(FamilyRole.MEMBER, session.getFamilyRole());
        assertFalse(session.isAdmin());
    }

    @Test
    void lastSyncTimeQuedaAisladoPorFamiliaActiva() {
        AppSession session = new AppSession(prefs);

        session.setFamilyId("fam-1");
        session.setLastSyncTime("2026-07-05T10:00:00Z");
        session.setFamilyId("fam-2");

        assertNull(session.getLastSyncTime());

        session.setLastSyncTime("2026-07-06T10:00:00Z");
        session.setFamilyId("fam-1");

        assertEquals("2026-07-05T10:00:00Z", session.getLastSyncTime());

        AppSession reloaded = new AppSession(prefs);
        assertEquals("fam-1", reloaded.getFamilyId());
        assertEquals("2026-07-05T10:00:00Z", reloaded.getLastSyncTime());
    }
}
