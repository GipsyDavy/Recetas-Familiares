package org.gipsybuho.recetasfamiliares.core

import platform.Foundation.NSUserDefaults

// MVP: NSUserDefaults (equivalente a SharedPreferences, no cifrado).
// Sprint 17+: migrar a Keychain para tokens de autenticación.
actual class SessionStore {
    private val prefs = NSUserDefaults.standardUserDefaults

    actual var accessToken: String?
        get() = prefs.stringForKey("rf_access_token")
        set(value) = if (value != null) prefs.setObject(value, "rf_access_token")
                     else prefs.removeObjectForKey("rf_access_token")

    actual var refreshToken: String?
        get() = prefs.stringForKey("rf_refresh_token")
        set(value) = if (value != null) prefs.setObject(value, "rf_refresh_token")
                     else prefs.removeObjectForKey("rf_refresh_token")

    actual var familyId: String?
        get() = prefs.stringForKey("rf_family_id")
        set(value) = if (value != null) prefs.setObject(value, "rf_family_id")
                     else prefs.removeObjectForKey("rf_family_id")

    actual var userId: String?
        get() = prefs.stringForKey("rf_user_id")
        set(value) = if (value != null) prefs.setObject(value, "rf_user_id")
                     else prefs.removeObjectForKey("rf_user_id")

    actual val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && !familyId.isNullOrBlank()

    actual fun clear() {
        listOf("rf_access_token", "rf_refresh_token", "rf_family_id", "rf_user_id")
            .forEach { prefs.removeObjectForKey(it) }
    }
}
