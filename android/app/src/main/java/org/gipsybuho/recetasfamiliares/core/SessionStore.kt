package org.gipsybuho.recetasfamiliares.core

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "recetas_session_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = preferences.getString("access_token", null)
        set(value) = preferences.edit { putString("access_token", value) }

    var refreshToken: String?
        get() = preferences.getString("refresh_token", null)
        set(value) = preferences.edit { putString("refresh_token", value) }

    var familyId: String?
        get() = preferences.getString("family_id", null)
        set(value) = preferences.edit { putString("family_id", value) }

    var userId: String?
        get() = preferences.getString("user_id", null)
        set(value) = preferences.edit { putString("user_id", value) }

    var displayName: String?
        get() = preferences.getString("display_name", null)
        set(value) = preferences.edit { putString("display_name", value) }

    var email: String?
        get() = preferences.getString("email", null)
        set(value) = preferences.edit { putString("email", value) }

    var lastSyncTime: String?
        get() = preferences.getString("last_sync_time", null)
        set(value) = preferences.edit { putString("last_sync_time", value) }

    fun clear() {
        preferences.edit { clear() }
    }
}
