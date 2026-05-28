package org.gipsybuho.recetasfamiliares.core;

import java.util.prefs.Preferences;

/** In-memory session with optional persistence via java.util.prefs. */
public class AppSession {

    private static final String PREF_NODE     = "recetas-familiares";
    private static final String KEY_ACCESS    = "accessToken";
    private static final String KEY_REFRESH   = "refreshToken";
    private static final String KEY_FAMILY_ID = "familyId";
    private static final String KEY_LAST_SYNC = "lastSyncTime";

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private String accessToken;
    private String refreshToken;
    private String familyId;
    private String lastSyncTime;

    public AppSession() {
        this.accessToken  = prefs.get(KEY_ACCESS,    null);
        this.refreshToken = prefs.get(KEY_REFRESH,   null);
        this.familyId     = prefs.get(KEY_FAMILY_ID, null);
        this.lastSyncTime = prefs.get(KEY_LAST_SYNC, null);
    }

    public boolean isLoggedIn() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getFamilyId() { return familyId; }
    public String getLastSyncTime() { return lastSyncTime; }

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

    public void clear() {
        accessToken  = null;
        refreshToken = null;
        familyId     = null;
        lastSyncTime = null;
        prefs.remove(KEY_ACCESS);
        prefs.remove(KEY_REFRESH);
        prefs.remove(KEY_FAMILY_ID);
        prefs.remove(KEY_LAST_SYNC);
    }
}
