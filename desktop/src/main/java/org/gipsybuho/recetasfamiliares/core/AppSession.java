package org.gipsybuho.recetasfamiliares.core;

import java.util.prefs.Preferences;

/**
 * In-memory session with optional persistence via java.util.prefs.
 *
 * SECURITY (SEC-2): access y refresh token se cifran con Windows DPAPI
 * (TokenVault) antes de persistirse en prefs. Valores legado en texto plano se
 * migran a formato cifrado en el primer arranque. En plataformas sin DPAPI
 * (Linux/macOS dev) se conserva el comportamiento previo en texto plano.
 */
public class AppSession {

    private static final String PREF_NODE        = "recetas-familiares";
    private static final String KEY_ACCESS       = "accessToken";
    private static final String KEY_REFRESH      = "refreshToken";
    private static final String KEY_FAMILY_ID    = "familyId";
    private static final String KEY_LAST_SYNC    = "lastSyncTime";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_EMAIL        = "email";
    private static final String KEY_FAMILY_ROLE  = "familyRole";
    private static final String KEY_AVATAR_URL   = "avatarUrl";

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private String accessToken;
    private String refreshToken;
    private String familyId;
    private String lastSyncTime;
    private String displayName;
    private String email;
    private FamilyRole familyRole;
    private String avatarUrl;

    public AppSession() {
        this.accessToken  = readToken(KEY_ACCESS);
        this.refreshToken = readToken(KEY_REFRESH);
        this.familyId     = prefs.get(KEY_FAMILY_ID,     null);
        this.lastSyncTime = prefs.get(KEY_LAST_SYNC,     null);
        this.displayName  = prefs.get(KEY_DISPLAY_NAME,  null);
        this.email        = prefs.get(KEY_EMAIL,         null);
        this.avatarUrl    = prefs.get(KEY_AVATAR_URL,    null);
        String roleStr    = prefs.get(KEY_FAMILY_ROLE,   null);
        this.familyRole   = parseFamilyRole(roleStr);
    }

    public boolean isLoggedIn() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String getAccessToken()  { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getFamilyId()     { return familyId; }
    public String getLastSyncTime() { return lastSyncTime; }
    public String getDisplayName()  { return displayName; }
    public String getEmail()        { return email; }
    public String getAvatarUrl()    { return avatarUrl; }
    public FamilyRole getFamilyRole() { return familyRole; }

    /** Returns true when the user has OWNER or ADMIN role in their family. */
    public boolean isAdmin() {
        return familyRole != null && familyRole.isAdminOrAbove();
    }

    public void setTokens(String accessToken, String refreshToken) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        if (accessToken  != null) prefs.put(KEY_ACCESS,  TokenVault.protect(accessToken));  else prefs.remove(KEY_ACCESS);
        if (refreshToken != null) prefs.put(KEY_REFRESH, TokenVault.protect(refreshToken)); else prefs.remove(KEY_REFRESH);
    }

    /** Lee un token persistido, descifrandolo y migrando valores legado en texto plano. */
    private String readToken(String key) {
        String stored = prefs.get(key, null);
        if (stored == null) {
            return null;
        }
        String plain = TokenVault.unprotect(stored);
        if (plain == null) {
            // Cifrado no recuperable (otro usuario/maquina): limpiar y pedir login
            prefs.remove(key);
            return null;
        }
        if (!TokenVault.isProtected(stored)) {
            // Migracion: re-persistir cifrado el valor legado en texto plano
            prefs.put(key, TokenVault.protect(plain));
        }
        return plain;
    }

    public void setFamilyId(String familyId) {
        this.familyId = familyId;
        if (familyId != null) prefs.put(KEY_FAMILY_ID, familyId);
        else prefs.remove(KEY_FAMILY_ID);
    }

    public void setLastSyncTime(String lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
        if (lastSyncTime != null) prefs.put(KEY_LAST_SYNC, lastSyncTime);
        else prefs.remove(KEY_LAST_SYNC);
    }

    public void setUserInfo(String displayName, String email) {
        this.displayName = displayName;
        this.email       = email;
        if (displayName != null) prefs.put(KEY_DISPLAY_NAME, displayName); else prefs.remove(KEY_DISPLAY_NAME);
        if (email       != null) prefs.put(KEY_EMAIL,        email);       else prefs.remove(KEY_EMAIL);
    }

    public void setFamilyRole(FamilyRole role) {
        this.familyRole = role;
        if (role != null) prefs.put(KEY_FAMILY_ROLE, role.name());
        else prefs.remove(KEY_FAMILY_ROLE);
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        if (avatarUrl != null) prefs.put(KEY_AVATAR_URL, avatarUrl);
        else prefs.remove(KEY_AVATAR_URL);
    }

    public void clear() {
        accessToken  = null;
        refreshToken = null;
        familyId     = null;
        lastSyncTime = null;
        displayName  = null;
        email        = null;
        familyRole   = null;
        avatarUrl    = null;
        prefs.remove(KEY_ACCESS);
        prefs.remove(KEY_REFRESH);
        prefs.remove(KEY_FAMILY_ID);
        prefs.remove(KEY_LAST_SYNC);
        prefs.remove(KEY_DISPLAY_NAME);
        prefs.remove(KEY_EMAIL);
        prefs.remove(KEY_FAMILY_ROLE);
        prefs.remove(KEY_AVATAR_URL);
    }

    private static FamilyRole parseFamilyRole(String str) {
        if (str == null || str.isBlank()) return null;
        try { return FamilyRole.valueOf(str); } catch (IllegalArgumentException e) { return FamilyRole.MEMBER; }
    }
}
