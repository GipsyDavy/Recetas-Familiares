package org.gipsybuho.recetasfamiliares.core;

import java.util.prefs.Preferences;

/**
 * In-memory session with optional persistence via java.util.prefs.
 *
 * SECURITY NOTE (MVP): Tokens are stored in java.util.prefs (Windows Registry /
 * ~/.java/.userPrefs on Linux). This storage is NOT encrypted. Acceptable for a
 * private family desktop app on a trusted device; for production use, replace with
 * OS credential store (Windows Credential Manager / macOS Keychain / libsecret).
 */
public class AppSession {

    private static final String PREF_NODE        = "recetas-familiares";
    private static final String KEY_ACCESS       = "accessToken";
    private static final String KEY_REFRESH      = "refreshToken";
    private static final String KEY_FAMILY_ID    = "familyId";
    private static final String KEY_LAST_SYNC    = "lastSyncTime";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_EMAIL        = "email";

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private String accessToken;
    private String refreshToken;
    private String familyId;
    private String lastSyncTime;
    private String displayName;
    private String email;

    public AppSession() {
        this.accessToken  = prefs.get(KEY_ACCESS,        null);
        this.refreshToken = prefs.get(KEY_REFRESH,       null);
        this.familyId     = prefs.get(KEY_FAMILY_ID,     null);
        this.lastSyncTime = prefs.get(KEY_LAST_SYNC,     null);
        this.displayName  = prefs.get(KEY_DISPLAY_NAME,  null);
        this.email        = prefs.get(KEY_EMAIL,         null);
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

    public void setTokens(String accessToken, String refreshToken) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        if (accessToken  != null) prefs.put(KEY_ACCESS,  accessToken);  else prefs.remove(KEY_ACCESS);
        if (refreshToken != null) prefs.put(KEY_REFRESH, refreshToken); else prefs.remove(KEY_REFRESH);
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

    public void clear() {
        accessToken  = null;
        refreshToken = null;
        familyId     = null;
        lastSyncTime = null;
        displayName  = null;
        email        = null;
        prefs.remove(KEY_ACCESS);
        prefs.remove(KEY_REFRESH);
        prefs.remove(KEY_FAMILY_ID);
        prefs.remove(KEY_LAST_SYNC);
        prefs.remove(KEY_DISPLAY_NAME);
        prefs.remove(KEY_EMAIL);
    }
}
